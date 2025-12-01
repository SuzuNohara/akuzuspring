package com.nexus.service;

import com.nexus.dto.*;
import com.nexus.entity.Event;
import com.nexus.entity.ExternalCalendar;
import com.nexus.entity.ExternalEvent;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.ExternalEventRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
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

        // Soft delete: marcar como inactivo
        calendar.setIsActive(false);
        calendar.setSyncEnabled(false);
        externalCalendarRepository.save(calendar);

        log.info("Calendario desvinculado: {}", calendar.getId());
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
        String privacyMode
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

        ExternalCalendar saved = externalCalendarRepository.save(calendar);
        return ExternalCalendarDTO.fromEntity(saved);
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

                if (existing.isPresent()) {
                    // RF-24: Detectar conflictos
                    ExternalEvent event = existing.get();
                    boolean hasConflict = detectConflict(event, request);
                    
                    if (hasConflict) {
                        conflicts.add(String.format(
                            "Evento '%s' fue modificado externamente",
                            event.getTitle()
                        ));
                    }

                    updateExistingEvent(event, request);
                    // Guardar y vaciar el contexto de persistencia para evitar assertions
                    externalEventRepository.saveAndFlush(event);
                    updated++;
                } else {
                    ExternalEvent newEvent = createNewEvent(request);
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
     * RF-24: Detectar si un evento fue modificado externamente
     * NOTA: Por ahora desactivado. Solo se debe activar para eventos que se crearon en la app
     * y se enviaron al calendario externo (funcionalidad aún no implementada).
     */
    private boolean detectConflict(ExternalEvent existing, SyncEventRequest incoming) {
        // TODO: Implementar detección de conflictos solo para eventos originados en la app
        // Por ahora, todos los eventos externos no generan conflictos
        return false;
        
        /* Lógica futura:
        // Verificar si el evento tiene un eventId de la app (fue creado aquí)
        if (existing.getAppEventId() == null) {
            return false; // Evento puramente externo, no hay conflicto
        }
        
        // Si no tenemos lastDeviceUpdate, asumir que no hay conflicto
        if (incoming.getLastDeviceUpdate() == null || existing.getLastDeviceUpdate() == null) {
            return false;
        }

        // Si el evento fue modificado en el dispositivo después de nuestra última actualización
        return incoming.getLastDeviceUpdate().isAfter(existing.getLastDeviceUpdate());
        */
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
    }

    private ExternalEvent createNewEvent(SyncEventRequest request) {
        ExternalEvent event = new ExternalEvent();
        event.setExternalCalendarId(request.getExternalCalendarId());
        event.setDeviceEventId(request.getDeviceEventId());
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
        allEvents.addAll(getUserExternalEvents(userId, userId, startDate, endDate));
        
        // Si hay partnerId, obtener también eventos de la pareja
        if (partnerId != null) {
            allEvents.addAll(getUserExternalEvents(partnerId, partnerId, startDate, endDate));
        }
        
        return allEvents;
    }
    
    /**
     * Obtener eventos externos de un usuario específico con privacidad aplicada
     */
    private List<ExternalEventDTO> getUserExternalEvents(
        Long userId,
        Long ownerId,
        Instant startDate,
        Instant endDate
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

        return events.stream()
            .map(event -> {
                ExternalCalendar calendar = calendarMap.get(event.getExternalCalendarId());
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
