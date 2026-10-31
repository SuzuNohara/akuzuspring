package com.nexus.service;

import com.nexus.dto.*;
import com.nexus.entity.Event;
import com.nexus.entity.ExternalCalendar;
import com.nexus.entity.ExternalEvent;
import com.nexus.exception.ForbiddenException;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.ExternalEventRepository;
import com.nexus.service.push.NoticeTypes;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalCalendarService {

    private final ExternalCalendarRepository externalCalendarRepository;
    private final ExternalEventRepository externalEventRepository;
    private final EventRepository eventRepository;
    private final AppNotificationService appNotifications;

    /** [avisos-calendario] Las horas de los avisos se dicen en hora de la CDMX. */
    private static final ZoneId CDMX = ZoneId.of("America/Mexico_City");
    private static final String[] WEEKDAYS = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * RF-19: Vincular un calendario externo
     */
    @Transactional
    public ExternalCalendarDTO linkCalendar(Long userId, LinkCalendarRequest request) {
        log.info("Vinculando calendario {} para usuario {}", request.getCalendarName(), userId);

        // Verificar si ya existe
        Optional<ExternalCalendar> existing = externalCalendarRepository
            .findByUserIdAndDeviceCalendarId(userId, request.getDeviceCalendarId());

        if (existing.isPresent()) {
            // Reactivar si estaba desactivado
            ExternalCalendar calendar = existing.get();
            calendar.setIsActive(true);
            calendar.setSyncEnabled(request.getSyncEnabled());
            calendar.setPrivacyMode(ExternalCalendar.PrivacyMode.valueOf(request.getPrivacyMode()));
            externalCalendarRepository.save(calendar);
            log.info("Calendario reactivado: {}", calendar.getId());
            return ExternalCalendarDTO.fromEntity(calendar);
        }

        // Crear nuevo
        ExternalCalendar calendar = new ExternalCalendar();
        calendar.setUserId(userId);
        calendar.setDeviceCalendarId(request.getDeviceCalendarId());
        calendar.setCalendarName(request.getCalendarName());
        // Determinar source basado en calendarSource
        calendar.setSource(determineCalendarSource(request.getCalendarSource()));
        calendar.setCalendarColor(request.getCalendarColor());
        calendar.setSyncEnabled(request.getSyncEnabled());
        calendar.setPrivacyMode(ExternalCalendar.PrivacyMode.valueOf(request.getPrivacyMode()));
        calendar.setIsActive(true);

        ExternalCalendar saved = externalCalendarRepository.save(calendar);
        log.info("Calendario vinculado exitosamente: {}", saved.getId());
        return ExternalCalendarDTO.fromEntity(saved);
    }

    /**
     * RF-26: Desvincular un calendario externo
     */
    @Transactional
    public void unlinkCalendar(Long userId, String deviceCalendarId) {
        log.info("Desvinculando calendario {} para usuario {}", deviceCalendarId, userId);

        ExternalCalendar calendar = externalCalendarRepository
            .findByUserIdAndDeviceCalendarId(userId, deviceCalendarId)
            .orElseThrow(() -> new RuntimeException("Calendario no encontrado"));

        // Eliminar todos los eventos asociados a este calendario
        List<ExternalEvent> eventsToDelete = externalEventRepository
            .findByExternalCalendarId(calendar.getId());
        
        if (!eventsToDelete.isEmpty()) {
            log.info("Eliminando {} eventos asociados al calendario {}", 
                eventsToDelete.size(), calendar.getId());
            externalEventRepository.deleteAll(eventsToDelete);
        }

        // Soft delete del calendario: marcar como inactivo
        calendar.setIsActive(false);
        calendar.setSyncEnabled(false);
        externalCalendarRepository.save(calendar);

        log.info("Calendario desvinculado y {} eventos eliminados", eventsToDelete.size());
    }

    /**
     * Obtener todos los calendarios vinculados de un usuario
     */
    public List<ExternalCalendarDTO> getUserCalendars(Long userId, Boolean activeOnly) {
        List<ExternalCalendar> calendars = activeOnly
            ? externalCalendarRepository.findByUserIdAndIsActiveTrue(userId)
            : externalCalendarRepository.findByUserId(userId);

        return calendars.stream()
            .map(ExternalCalendarDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * Actualizar configuración de un calendario
     */
    @Transactional
    public ExternalCalendarDTO updateCalendarSettings(
        Long userId,
        String deviceCalendarId,
        Boolean syncEnabled,
        String privacyMode,
        Boolean exportEvents
    ) {
        ExternalCalendar calendar = externalCalendarRepository
            .findByUserIdAndDeviceCalendarId(userId, deviceCalendarId)
            .orElseThrow(() -> new RuntimeException("Calendario no encontrado"));

        if (syncEnabled != null) {
            calendar.setSyncEnabled(syncEnabled);
        }
        if (privacyMode != null) {
            calendar.setPrivacyMode(ExternalCalendar.PrivacyMode.valueOf(privacyMode));
        }
        if (exportEvents != null) {
            setExportTarget(userId, calendar, exportEvents);
        }

        ExternalCalendar saved = externalCalendarRepository.save(calendar);
        return ExternalCalendarDTO.fromEntity(saved);
    }

    /**
     * [sync-calendario] RF-22 - Un solo calendario destino por persona. Al elegirlo se prende su
     * sincronizacion, para que RF-24 vea los cambios que se hagan en las copias (R9).
     */
    private void setExportTarget(Long userId, ExternalCalendar target, boolean export) {
        if (export) {
            for (ExternalCalendar other : externalCalendarRepository.findByUserIdAndIsActiveTrue(userId)) {
                if (!other.getId().equals(target.getId()) && Boolean.TRUE.equals(other.getExportEvents())) {
                    other.setExportEvents(false);
                    externalCalendarRepository.save(other);
                }
            }
            target.setSyncEnabled(true);
        }
        target.setExportEvents(export);
    }

    /**
     * RF-20 & RF-21: Sincronizar eventos desde el dispositivo
     */
    @Transactional
    public Map<String, Object> syncEvents(Long userId, List<SyncEventRequest> events) {
        log.info("Sincronizando {} eventos para usuario {}", events.size(), userId);

        int created = 0;
        int updated = 0;
        List<String> conflicts = new ArrayList<>();

        for (SyncEventRequest request : events) {
            try {
                Optional<ExternalEvent> existing = externalEventRepository
                    .findByExternalCalendarIdAndDeviceEventId(
                        request.getExternalCalendarId(),
                        request.getDeviceEventId()
                    );

                boolean fromNexus = Boolean.TRUE.equals(request.getFromNexus());
                if (existing.isPresent()) {
                    // RF-24: solo una copia de un evento creado en Nexus puede estar en conflicto.
                    // [sync-calendario] Se compara contra lo guardado antes de actualizarlo.
                    ExternalEvent event = existing.get();
                    Optional<Conflict> conflict = fromNexus ? Optional.empty() : conflictOf(event, request);
                    updateExistingEvent(event, request);
                    if (fromNexus) {
                        event.setExportSignature(signatureOfNexusEvent(event.getAppEventId()));
                    }
                    if (conflict.isEmpty()) {
                        event.setConflictHash(null);
                    } else if (!conflict.get().hash().equals(event.getConflictHash())) {
                        // Se avisa una sola vez por cambio; la siguiente sincronizacion trae lo mismo.
                        event.setConflictHash(conflict.get().hash());
                        notifyConflict(userId, conflict.get().nexusEvent());
                        conflicts.add(conflict.get().nexusEvent().getTitle());
                    }
                    // Guardar y vaciar el contexto de persistencia para evitar assertions
                    externalEventRepository.saveAndFlush(event);
                    updated++;
                } else {
                    ExternalEvent newEvent = createNewEvent(request);
                    if (fromNexus) {
                        newEvent.setExportSignature(signatureOfNexusEvent(newEvent.getAppEventId()));
                    }
                    externalEventRepository.saveAndFlush(newEvent);
                    created++;
                }
            } catch (Exception e) {
                log.error("Error sincronizando evento: {}", e.getMessage());
            }
        }

        // Actualizar última sincronización fuera del bucle y con flush
        if (!events.isEmpty()) {
            Long calendarId = events.get(0).getExternalCalendarId();
            externalCalendarRepository.findById(calendarId).ifPresent(cal -> {
                cal.setLastSync(Instant.now());
                externalCalendarRepository.saveAndFlush(cal);
            });
        }

        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("conflicts", conflicts);
        result.put("total", events.size());

        log.info("Sincronización completada: {} creados, {} actualizados, {} conflictos",
            created, updated, conflicts.size());

        return result;
    }

    /**
     * [sync-calendario] RF-22 - La app quito esta copia del telefono (o ya no estaba ahi): se borra
     * (logico) para que el siguiente plan la vuelva a crear si hace falta.
     *
     * @throws ForbiddenException si el calendario no es de la persona
     */
    @Transactional
    public void deleteCopy(Long userId, Long externalCalendarId, String deviceEventId) {
        ExternalCalendar calendar = externalCalendarRepository.findById(externalCalendarId)
            .filter(c -> c.getUserId().equals(userId))
            .orElseThrow(() -> new ForbiddenException("El calendario no es tuyo"));
        externalEventRepository.softDeleteByExternalCalendarIdAndDeviceEventId(calendar.getId(), deviceEventId);
    }

    /** [sync-calendario] Firma del evento de Nexus que se acaba de escribir en el telefono. */
    private String signatureOfNexusEvent(Long appEventId) {
        return appEventId == null ? null
                : eventRepository.findById(appEventId).map(CalendarCopyPlanner::signatureOf).orElse(null);
    }

    /** [avisos-calendario] Copia del telefono que ya no coincide con su evento de Nexus. */
    record Conflict(Event nexusEvent, String hash) {}

    /**
     * RF-24 - La copia esta en conflicto si salio de un evento de Nexus que sigue existiendo, se
     * cambio en el telefono (ya no es lo guardado) y no quedo igual que Nexus. Si solo cambio Nexus,
     * el telefono trae lo guardado y no es conflicto ([sync-calendario]). Un evento personal (sin
     * {@code appEventId}) nunca esta en conflicto, aunque cambie. Las horas se comparan al minuto y
     * el titulo sin espacios sobrantes.
     *
     * @param copy la copia como estaba guardada, antes de actualizarla
     * @return la huella de lo que trae el telefono, para avisar una sola vez por cambio
     */
    Optional<Conflict> conflictOf(ExternalEvent copy, SyncEventRequest incoming) {
        if (copy.getAppEventId() == null) {
            return Optional.empty();
        }
        // [sync-calendario] De una serie el telefono manda la primera ocurrencia de la ventana, no el
        // inicio de la serie: ahi solo cuentan la hora del dia y la duracion.
        boolean series = incoming.getRecurrenceRule() != null && !incoming.getRecurrenceRule().isBlank();
        boolean unchanged = sameSlot(copy.getTitle(), copy.getStartDatetime(), copy.getEndDatetime(), incoming, series);
        if (unchanged) {
            return Optional.empty();
        }
        Optional<Event> nexus = eventRepository.findById(copy.getAppEventId()).filter(e -> e.getDeletedAt() == null);
        if (nexus.isEmpty()) {
            return Optional.empty();
        }
        Event e = nexus.get();
        if (sameSlot(e.getTitle(), e.getStartDateTime(), e.getEndDateTime(), incoming, series)) {
            return Optional.empty();
        }
        String hash = sha256(normalized(incoming.getTitle()) + "|" + minute(incoming.getStartDatetime())
                + "|" + minute(incoming.getEndDatetime()));
        return Optional.of(new Conflict(e, hash));
    }

    /**
     * Mismo titulo y mismas horas al minuto; en una serie, misma hora del dia (CDMX) y misma
     * duracion, sin importar la fecha.
     */
    private static boolean sameSlot(String title, Instant start, Instant end, SyncEventRequest incoming, boolean series) {
        if (!normalized(title).equals(normalized(incoming.getTitle()))) {
            return false;
        }
        if (!series) {
            return sameMinute(start, incoming.getStartDatetime()) && sameMinute(end, incoming.getEndDatetime());
        }
        if (start == null || end == null || incoming.getStartDatetime() == null || incoming.getEndDatetime() == null) {
            return false;
        }
        boolean sameTime = start.atZone(CDMX).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                .equals(incoming.getStartDatetime().atZone(CDMX).toLocalTime().truncatedTo(ChronoUnit.MINUTES));
        boolean sameLength = ChronoUnit.MINUTES.between(start, end)
                == ChronoUnit.MINUTES.between(incoming.getStartDatetime(), incoming.getEndDatetime());
        return sameTime && sameLength;
    }

    /** Aviso en la campanita (y push): que cambio y como sigue en Nexus. */
    private void notifyConflict(Long userId, Event nexus) {
        ZonedDateTime start = nexus.getStartDateTime().atZone(CDMX);
        ZonedDateTime end = nexus.getEndDateTime().atZone(CDMX);
        String when = WEEKDAYS[start.getDayOfWeek().getValue() - 1] + " " + start.getDayOfMonth()
                + " de " + start.format(HOUR) + " a " + end.format(HOUR);
        appNotifications.notify(userId, null, NoticeTypes.SYNC_CONFLICT, "Cambió un evento en tu calendario",
                "\"" + nexus.getTitle() + "\" cambió en tu calendario del teléfono. En Nexus sigue el " + when + ".",
                Map.of("eventId", String.valueOf(nexus.getId())));
    }

    private static String normalized(String title) {
        return title == null ? "" : title.trim().replaceAll("\\s+", " ");
    }

    private static boolean sameMinute(Instant a, Instant b) {
        return Objects.equals(minute(a), minute(b));
    }

    private static Instant minute(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MINUTES);
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void updateExistingEvent(ExternalEvent event, SyncEventRequest request) {
        event.setTitle(request.getTitle());
        event.setStartDatetime(request.getStartDatetime());
        event.setEndDatetime(request.getEndDatetime());
        event.setLocation(request.getLocation());
        event.setDescription(request.getDescription());
        event.setIsAllDay(request.getIsAllDay());
        event.setRecurrenceRule(request.getRecurrenceRule());
        event.setRruleDtstartUtc(request.getRruleDtstartUtc());
        event.setRruleUntilUtc(request.getRruleUntilUtc());
        event.setRruleCount(request.getRruleCount());
        event.setIsExternal(true);
        event.setLastDeviceUpdate(request.getLastDeviceUpdate());
        event.setSyncHash(generateHash(request));
        // La sincronizacion periodica no sabe de que evento de Nexus salio la copia: se conserva.
        if (request.getAppEventId() != null) {
            event.setAppEventId(request.getAppEventId());
        }
    }

    private ExternalEvent createNewEvent(SyncEventRequest request) {
        ExternalEvent event = new ExternalEvent();
        event.setExternalCalendarId(request.getExternalCalendarId());
        event.setDeviceEventId(request.getDeviceEventId());
        event.setAppEventId(request.getAppEventId());
        event.setTitle(request.getTitle());
        event.setStartDatetime(request.getStartDatetime());
        event.setEndDatetime(request.getEndDatetime());
        event.setStartTimezone("UTC");
        event.setEndTimezone("UTC");
        event.setLocation(request.getLocation());
        event.setDescription(request.getDescription());
        event.setIsAllDay(request.getIsAllDay());
        event.setRecurrenceRule(request.getRecurrenceRule());
        event.setRruleDtstartUtc(request.getRruleDtstartUtc());
        event.setRruleUntilUtc(request.getRruleUntilUtc());
        event.setRruleCount(request.getRruleCount());
        event.setIsExternal(true);
        event.setLastDeviceUpdate(request.getLastDeviceUpdate());
        event.setSyncHash(generateHash(request));
        event.setVisibility(ExternalEvent.Visibility.DEFAULT);
        event.setStatus(ExternalEvent.EventStatus.CONFIRMED);
        return event;
    }

    /**
     * Generar hash para detectar cambios
     */
    private String generateHash(SyncEventRequest request) {
        try {
            String data = String.format("%s|%s|%s|%s",
                request.getTitle(),
                request.getStartDatetime(),
                request.getEndDatetime(),
                request.getLocation() != null ? request.getLocation() : ""
            );
            
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * RF-23: Obtener eventos con privacidad aplicada (del usuario Y su pareja)
     */
    public List<ExternalEventDTO> getEventsWithPrivacy(
        Long userId,
        Instant startDate,
        Instant endDate,
        Long partnerId
    ) {
        List<ExternalEventDTO> allEvents = new ArrayList<>();
        
        // Obtener eventos del usuario actual
        allEvents.addAll(getUserExternalEvents(userId, userId, startDate, endDate, false));
        
        // Si hay partnerId, obtener también eventos de la pareja
        if (partnerId != null) {
            allEvents.addAll(getUserExternalEvents(partnerId, partnerId, startDate, endDate, true));
        }
        
        return allEvents;
    }
    
    /**
     * Obtener eventos externos de un usuario específico. La privacidad del calendario (RF-23) solo
     * se aplica cuando los ve la pareja ({@code masked}); su dueña siempre ve el detalle.
     */
    private List<ExternalEventDTO> getUserExternalEvents(
        Long userId,
        Long ownerId,
        Instant startDate,
        Instant endDate,
        boolean masked
    ) {
        // Obtener calendarios activos del usuario
        List<ExternalCalendar> calendars = externalCalendarRepository
            .findByUserIdAndIsActiveTrue(userId);

        if (calendars.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> calendarIds = calendars.stream()
            .map(ExternalCalendar::getId)
            .collect(Collectors.toList());

        // Obtener eventos en el rango de fechas
        List<ExternalEvent> events = externalEventRepository
            .findEventsByCalendarsAndDateRange(calendarIds, startDate, endDate);

        // Aplicar privacidad según configuración del calendario
        Map<Long, ExternalCalendar> calendarMap = calendars.stream()
            .collect(Collectors.toMap(ExternalCalendar::getId, cal -> cal));

        // [sync-calendario] Las copias de eventos de Nexus no se muestran: el evento ya sale de Nexus.
        return events.stream()
            .filter(event -> event.getAppEventId() == null)
            .map(event -> {
                ExternalCalendar calendar = calendarMap.get(event.getExternalCalendarId());
                if (!masked) {
                    return ExternalEventDTO.fromEntity(event, ownerId);
                }
                return ExternalEventDTO.fromEntityWithPrivacy(
                    event,
                    ownerId,
                    calendar != null ? calendar.getPrivacyMode() : ExternalCalendar.PrivacyMode.BUSY_ONLY
                );
            })
            .collect(Collectors.toList());
    }

    /**
     * RF-27: Identificar espacios libres individuales
     */
    public List<AvailabilitySlot> findFreeSlots(
        Long userId,
        Instant startDate,
        Instant endDate,
        Integer minDurationMinutes
    ) {
        log.info("Buscando espacios libres para usuario {} entre {} y {}", 
            userId, startDate, endDate);

        // Obtener todos los eventos del usuario (internos + externos)
        List<Instant[]> busySlots = new ArrayList<>();

        // Eventos internos
        List<Event> internalEvents = eventRepository
            .findByCreatorIdAndStartDateTimeBetween(userId, startDate, endDate);
        internalEvents.forEach(event -> 
            busySlots.add(new Instant[]{event.getStartDateTime(), event.getEndDateTime()})
        );

        // Eventos externos
        List<ExternalCalendar> calendars = externalCalendarRepository
            .findByUserIdAndIsActiveTrue(userId);
        if (!calendars.isEmpty()) {
            List<Long> calendarIds = calendars.stream()
                .map(ExternalCalendar::getId)
                .collect(Collectors.toList());
            
            List<ExternalEvent> externalEvents = externalEventRepository
                .findEventsByCalendarsAndDateRange(calendarIds, startDate, endDate);
            externalEvents.forEach(event ->
                busySlots.add(new Instant[]{event.getStartDatetime(), event.getEndDatetime()})
            );
        }

        // Ordenar slots ocupados
        busySlots.sort(Comparator.comparing(slot -> slot[0]));

        // Encontrar huecos libres
        List<AvailabilitySlot> freeSlots = new ArrayList<>();
        Instant currentTime = startDate;

        for (Instant[] busySlot : busySlots) {
            Instant busyStart = busySlot[0];
            Instant busyEnd = busySlot[1];

            // Si hay un gap antes de este evento
            long gapMinutes = ChronoUnit.MINUTES.between(currentTime, busyStart);
            if (gapMinutes >= minDurationMinutes) {
                freeSlots.add(new AvailabilitySlot(currentTime, busyStart));
            }

            // Avanzar el tiempo actual
            if (busyEnd.isAfter(currentTime)) {
                currentTime = busyEnd;
            }
        }

        // Agregar el último slot libre
        long finalGapMinutes = ChronoUnit.MINUTES.between(currentTime, endDate);
        if (finalGapMinutes >= minDurationMinutes) {
            freeSlots.add(new AvailabilitySlot(currentTime, endDate));
        }

        log.info("Encontrados {} espacios libres", freeSlots.size());
        return freeSlots;
    }

    /**
     * RF-28: Cruce de disponibilidad entre dos usuarios
     */
    public List<AvailabilitySlot> findMutualAvailability(
        Long user1Id,
        Long user2Id,
        Instant startDate,
        Instant endDate,
        Integer minDurationMinutes
    ) {
        log.info("Buscando disponibilidad mutua entre usuarios {} y {}", user1Id, user2Id);

        // Obtener espacios libres de cada usuario
        List<AvailabilitySlot> user1Free = findFreeSlots(user1Id, startDate, endDate, minDurationMinutes);
        List<AvailabilitySlot> user2Free = findFreeSlots(user2Id, startDate, endDate, minDurationMinutes);

        // Encontrar intersecciones
        List<AvailabilitySlot> mutualSlots = new ArrayList<>();

        for (AvailabilitySlot slot1 : user1Free) {
            for (AvailabilitySlot slot2 : user2Free) {
                Instant overlapStart = slot1.getStart().isAfter(slot2.getStart()) 
                    ? slot1.getStart() : slot2.getStart();
                Instant overlapEnd = slot1.getEnd().isBefore(slot2.getEnd()) 
                    ? slot1.getEnd() : slot2.getEnd();

                long overlapMinutes = ChronoUnit.MINUTES.between(overlapStart, overlapEnd);
                
                if (overlapMinutes >= minDurationMinutes) {
                    mutualSlots.add(new AvailabilitySlot(overlapStart, overlapEnd));
                }
            }
        }

        log.info("Encontrados {} espacios mutuos disponibles", mutualSlots.size());
        return mutualSlots;
    }

    /**
     * Eliminar eventos huérfanos (cuyos calendarios fueron desvinculados)
     */
    @Transactional
    public void cleanupOrphanedEvents() {
        List<ExternalCalendar> inactiveCalendars = externalCalendarRepository
            .findAll()
            .stream()
            .filter(cal -> !cal.getIsActive())
            .collect(Collectors.toList());

        for (ExternalCalendar calendar : inactiveCalendars) {
            externalEventRepository.softDeleteByExternalCalendarId(calendar.getId());
        }
    }

    /**
     * Helper para determinar el source del calendario
     */
    private ExternalCalendar.CalendarSource determineCalendarSource(String sourceString) {
        if (sourceString == null) {
            return ExternalCalendar.CalendarSource.LOCAL;
        }
        
        String lower = sourceString.toLowerCase();
        if (lower.contains("google")) {
            return ExternalCalendar.CalendarSource.GOOGLE;
        } else if (lower.contains("outlook") || lower.contains("microsoft")) {
            return ExternalCalendar.CalendarSource.OUTLOOK;
        }
        return ExternalCalendar.CalendarSource.LOCAL;
    }
}
