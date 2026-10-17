package com.nexus.backend.service;

import com.nexus.dto.AvailabilityScheduleDTO;
import com.nexus.entity.AvailabilitySchedule;
import com.nexus.entity.Event;
import com.nexus.entity.ExternalCalendar;
import com.nexus.entity.ExternalEvent;
import com.nexus.repository.AvailabilityScheduleRepository;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.ExternalEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    @Autowired
    private AvailabilityScheduleRepository availabilityScheduleRepository;

    @Autowired
    private EventRepository eventRepository;
    
    @Autowired
    private ExternalCalendarRepository externalCalendarRepository;
    
    @Autowired
    private ExternalEventRepository externalEventRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId MEXICO_ZONE = ZoneId.of("America/Mexico_City");

    /**
     * Calcula los espacios disponibles de un usuario para los próximos N días
     * considerando sus horarios permitidos, eventos de la app Y eventos externos (personales)
     */
    public Map<String, Object> calculateAvailableSlots(Long userId, int daysAhead) {
        List<AvailabilitySchedule> schedules = availabilityScheduleRepository.findEnabledByUserId(userId);
        
        if (schedules.isEmpty()) {
            return Map.of(
                "userId", userId,
                "availableSlots", Collections.emptyList(),
                "message", "No hay horarios configurados"
            );
        }

        LocalDate startDate = LocalDate.now(MEXICO_ZONE);
        LocalDate endDate = startDate.plusDays(daysAhead);
        Instant rangeStart = startDate.atStartOfDay().atZone(MEXICO_ZONE).toInstant();
        Instant rangeEnd = endDate.atTime(23, 59, 59).atZone(MEXICO_ZONE).toInstant();

        // Obtener eventos de la app del usuario (solo CONFIRMADOS)
        List<Event> appEvents = eventRepository.findByUserIdAndDateRange(userId, rangeStart, rangeEnd);
        List<Event> confirmedAppEvents = appEvents.stream()
            .filter(e -> e.getStatus() == com.nexus.entity.EventStatus.CONFIRMED)
            .collect(Collectors.toList());

        // Obtener eventos externos (personales) del usuario
        List<ExternalCalendar> allCalendars = externalCalendarRepository.findByUserId(userId);
        System.out.println("📋 Usuario " + userId + " - TOTAL calendarios: " + allCalendars.size());
        for (ExternalCalendar cal : allCalendars) {
            System.out.println("   📅 " + cal.getCalendarName() + " - Active:" + cal.getIsActive() + 
                             " Deleted:" + (cal.getDeletedAt() != null) + " ID:" + cal.getId());
        }
        
        List<ExternalCalendar> userCalendars = externalCalendarRepository.findByUserIdAndIsActiveTrue(userId);
        System.out.println("📱 Usuario " + userId + " - Calendarios externos ACTIVOS: " + userCalendars.size());
        for (ExternalCalendar cal : userCalendars) {
            System.out.println("   ✅ Calendario: " + cal.getCalendarName() + " (ID: " + cal.getId() + ")");
        }
        
        List<Long> calendarIds = userCalendars.stream()
            .map(ExternalCalendar::getId)
            .collect(Collectors.toList());
        
        List<ExternalEvent> externalEvents = Collections.emptyList();
        if (!calendarIds.isEmpty()) {
            List<ExternalEvent> baseEvents = externalEventRepository.findEventsByCalendarsAndDateRange(
                calendarIds, 
                rangeStart, 
                rangeEnd
            );
            
            // Debug: Verificar contenido de rrule
            System.out.println("🔎 DEBUG - Revisando " + baseEvents.size() + " eventos base:");
            for (ExternalEvent evt : baseEvents) {
                System.out.println("   📅 " + evt.getTitle() + 
                    " | rrule: " + (evt.getRecurrenceRule() != null ? evt.getRecurrenceRule() : "NULL") +
                    " | rrule_dtstart: " + evt.getRruleDtstartUtc() +
                    " | starts_at: " + evt.getStartDatetime());
            }
            
            // Los eventos de todo el dia (cumpleanos, aniversarios) no ocupan tiempo, como en Google
            // Calendar por defecto. Ademas vienen a las 00:00 UTC y en la CDMX se corrian 6 horas.
            List<ExternalEvent> timedEvents = baseEvents.stream()
                .filter(evt -> !Boolean.TRUE.equals(evt.getIsAllDay()))
                .collect(Collectors.toList());

            // Expandir eventos recurrentes
            externalEvents = expandRecurringEvents(timedEvents, rangeStart, rangeEnd);
            
            System.out.println("📆 Eventos externos base: " + baseEvents.size() + ", expandidos: " + externalEvents.size());
        } else {
            System.out.println("⚠️  Sin calendarios externos activos para usuario " + userId);
        }

        System.out.println("🔍 Usuario " + userId + " - Eventos app CONFIRMADOS: " + confirmedAppEvents.size() + 
                         ", Eventos externos: " + externalEvents.size());

        // Calcular slots disponibles
        List<Map<String, Object>> availableSlots = new ArrayList<>();
        
        for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            String dayName = dayOfWeek.name();
            
            // Buscar configuración para este día
            Optional<AvailabilitySchedule> scheduleOpt = schedules.stream()
                .filter(s -> s.getDayOfWeek().name().equals(dayName))
                .findFirst();
            
            if (scheduleOpt.isEmpty()) {
                continue; // Día no configurado
            }
            
            AvailabilitySchedule schedule = scheduleOpt.get();
            
            // Obtener eventos de la app de este día
            LocalDate currentDate = date;
            List<Event> dayAppEvents = confirmedAppEvents.stream()
                .filter(e -> {
                    LocalDateTime eventStart = LocalDateTime.ofInstant(e.getStartDateTime(), MEXICO_ZONE);
                    LocalDateTime eventEnd = LocalDateTime.ofInstant(e.getEndDateTime(), MEXICO_ZONE);
                    LocalDateTime dayStart = currentDate.atStartOfDay();
                    LocalDateTime dayEnd = currentDate.atTime(23, 59, 59);
                    return eventStart.isBefore(dayEnd) && eventEnd.isAfter(dayStart);
                })
                .collect(Collectors.toList());
            
            // Obtener eventos externos de este día
            List<ExternalEvent> dayExternalEvents = externalEvents.stream()
                .filter(e -> {
                    LocalDateTime eventStart = LocalDateTime.ofInstant(e.getStartDatetime(), MEXICO_ZONE);
                    LocalDateTime eventEnd = LocalDateTime.ofInstant(e.getEndDatetime(), MEXICO_ZONE);
                    LocalDateTime dayStart = currentDate.atStartOfDay();
                    LocalDateTime dayEnd = currentDate.atTime(23, 59, 59);
                    return eventStart.isBefore(dayEnd) && eventEnd.isAfter(dayStart);
                })
                .collect(Collectors.toList());
            
            // Combinar todos los eventos y ordenarlos
            List<Object> allDayEvents = new ArrayList<>();
            allDayEvents.addAll(dayAppEvents);
            allDayEvents.addAll(dayExternalEvents);
            
            // Calcular slots libres considerando TODOS los eventos
            List<Map<String, String>> freeSlots = calculateFreeSlotsForDayWithAllEvents(
                date, 
                schedule.getStartTime(), 
                schedule.getEndTime(), 
                dayAppEvents,
                dayExternalEvents
            );
            
            if (!freeSlots.isEmpty()) {
                availableSlots.add(Map.of(
                    "date", date.toString(),
                    "dayOfWeek", dayName,
                    "freeSlots", freeSlots,
                    "totalFreeMinutes", calculateTotalMinutes(freeSlots)
                ));
            }
        }
        
        return Map.of(
            "userId", userId,
            "availableSlots", availableSlots,
            "totalDaysWithAvailability", availableSlots.size()
        );
    }

    /**
     * Calcula los slots libres para un día considerando eventos de la app Y eventos externos
     */
    private List<Map<String, String>> calculateFreeSlotsForDayWithAllEvents(
        LocalDate date,
        LocalTime allowedStart,
        LocalTime allowedEnd,
        List<Event> appEvents,
        List<ExternalEvent> externalEvents
    ) {
        System.out.println("🗓️  Calculando slots libres para " + date + " (rango: " + allowedStart + " - " + allowedEnd + ")");
        System.out.println("   Eventos app: " + appEvents.size() + ", Eventos externos: " + externalEvents.size());
        
        List<Map<String, String>> freeSlots = new ArrayList<>();
        
        // Convertir todos los eventos a slots de tiempo del día específico
        List<TimeSlot> daySlots = new ArrayList<>();
        
        // Añadir eventos de la app
        for (Event event : appEvents) {
            LocalDateTime eventStartDT = LocalDateTime.ofInstant(event.getStartDateTime(), MEXICO_ZONE);
            LocalDateTime eventEndDT = LocalDateTime.ofInstant(event.getEndDateTime(), MEXICO_ZONE);
            System.out.println("   📅 Evento app: " + event.getTitle() + " (" + eventStartDT + " - " + eventEndDT + ")");
            
            // Ajustar al día específico
            LocalTime start = eventStartDT.toLocalDate().equals(date) 
                ? eventStartDT.toLocalTime() 
                : LocalTime.MIN;
            LocalTime end = eventEndDT.toLocalDate().equals(date)
                ? eventEndDT.toLocalTime()
                : LocalTime.MAX;
                
            // Solo agregar si está dentro del rango permitido
            if (end.isAfter(allowedStart) && start.isBefore(allowedEnd)) {
                // Limitar al rango permitido
                if (start.isBefore(allowedStart)) start = allowedStart;
                if (end.isAfter(allowedEnd)) end = allowedEnd;
                
                daySlots.add(new TimeSlot(
                    date.atTime(start).atZone(MEXICO_ZONE).toInstant(),
                    date.atTime(end).atZone(MEXICO_ZONE).toInstant()
                ));
            }
        }
        
        // Añadir eventos externos
        for (ExternalEvent event : externalEvents) {
            LocalDateTime eventStartDT = LocalDateTime.ofInstant(event.getStartDatetime(), MEXICO_ZONE);
            LocalDateTime eventEndDT = LocalDateTime.ofInstant(event.getEndDatetime(), MEXICO_ZONE);
            System.out.println("   📱 Evento externo: " + event.getTitle() + " (" + eventStartDT + " - " + eventEndDT + ")");
            
            // Ajustar al día específico
            LocalTime start = eventStartDT.toLocalDate().equals(date) 
                ? eventStartDT.toLocalTime() 
                : LocalTime.MIN;
            LocalTime end = eventEndDT.toLocalDate().equals(date)
                ? eventEndDT.toLocalTime()
                : LocalTime.MAX;
                
            // Solo agregar si está dentro del rango permitido
            if (end.isAfter(allowedStart) && start.isBefore(allowedEnd)) {
                // Limitar al rango permitido
                if (start.isBefore(allowedStart)) start = allowedStart;
                if (end.isAfter(allowedEnd)) end = allowedEnd;
                
                daySlots.add(new TimeSlot(
                    date.atTime(start).atZone(MEXICO_ZONE).toInstant(),
                    date.atTime(end).atZone(MEXICO_ZONE).toInstant()
                ));
            }
        }
        
        // Si no hay eventos, todo el día está libre
        if (daySlots.isEmpty()) {
            System.out.println("   ✅ Sin eventos - Todo el día libre: " + allowedStart + " - " + allowedEnd);
            freeSlots.add(Map.of(
                "start", allowedStart.format(TIME_FORMATTER),
                "end", allowedEnd.format(TIME_FORMATTER)
            ));
            return freeSlots;
        }
        
        System.out.println("   🔢 Total eventos en rango permitido: " + daySlots.size());
        
        // Ordenar por hora de inicio
        daySlots.sort(Comparator.comparing(TimeSlot::getStart));
        
        // Fusionar eventos solapados
        List<TimeSlot> mergedSlots = new ArrayList<>();
        TimeSlot current = daySlots.get(0);
        
        for (int i = 1; i < daySlots.size(); i++) {
            TimeSlot next = daySlots.get(i);
            
            // Si se solapan o son contiguos, fusionar
            if (!current.getEnd().isBefore(next.getStart())) {
                // Extender el slot actual si el siguiente termina más tarde
                if (next.getEnd().isAfter(current.getEnd())) {
                    current = new TimeSlot(current.getStart(), next.getEnd());
                }
            } else {
                // No se solapan, guardar el actual y empezar uno nuevo
                mergedSlots.add(current);
                current = next;
            }
        }
        mergedSlots.add(current);
        
        System.out.println("   🔗 Eventos fusionados: " + mergedSlots.size());
        for (TimeSlot slot : mergedSlots) {
            LocalTime start = LocalDateTime.ofInstant(slot.getStart(), MEXICO_ZONE).toLocalTime();
            LocalTime end = LocalDateTime.ofInstant(slot.getEnd(), MEXICO_ZONE).toLocalTime();
            System.out.println("      Ocupado: " + start + " - " + end);
        }
        
        // Calcular espacios libres entre eventos fusionados
        LocalTime currentTime = allowedStart;
        
        for (TimeSlot slot : mergedSlots) {
            LocalTime eventStart = LocalDateTime.ofInstant(slot.getStart(), MEXICO_ZONE).toLocalTime();
            LocalTime eventEnd = LocalDateTime.ofInstant(slot.getEnd(), MEXICO_ZONE).toLocalTime();
            
            // Si hay espacio antes del evento
            if (currentTime.isBefore(eventStart)) {
                freeSlots.add(Map.of(
                    "start", currentTime.format(TIME_FORMATTER),
                    "end", eventStart.format(TIME_FORMATTER)
                ));
            }
            
            // Mover al final del evento
            currentTime = eventEnd;
        }
        
        // Si queda tiempo después del último evento
        if (currentTime.isBefore(allowedEnd)) {
            freeSlots.add(Map.of(
                "start", currentTime.format(TIME_FORMATTER),
                "end", allowedEnd.format(TIME_FORMATTER)
            ));
        }
        
        System.out.println("   ✅ Slots libres calculados: " + freeSlots.size());
        for (Map<String, String> slot : freeSlots) {
            System.out.println("      Libre: " + slot.get("start") + " - " + slot.get("end"));
        }
        
        return freeSlots;
    }
    
    /**
     * Clase auxiliar para manejar slots de tiempo
     */
    private static class TimeSlot {
        private final Instant start;
        private final Instant end;
        
        public TimeSlot(Instant start, Instant end) {
            this.start = start;
            this.end = end;
        }
        
        public Instant getStart() { return start; }
        public Instant getEnd() { return end; }
    }
    
    /**
     * Calcula minutos totales de una lista de slots
     */
    private int calculateTotalMinutes(List<Map<String, String>> slots) {
        int total = 0;
        for (Map<String, String> slot : slots) {
            LocalTime start = LocalTime.parse(slot.get("start"), TIME_FORMATTER);
            LocalTime end = LocalTime.parse(slot.get("end"), TIME_FORMATTER);
            total += Duration.between(start, end).toMinutes();
        }
        return total;
    }

    /**
     * Calcula los slots libres para un día específico (DEPRECADO - usar calculateFreeSlotsForDayWithAllEvents)
     */
    private List<Map<String, String>> calculateFreeSlotsForDay(
        LocalDate date,
        LocalTime allowedStart,
        LocalTime allowedEnd,
        List<Event> dayEvents
    ) {
        List<Map<String, String>> freeSlots = new ArrayList<>();
        
        LocalTime currentTime = allowedStart;
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(23, 59, 59);
        
        for (Event event : dayEvents) {
            LocalDateTime eventStartDT = LocalDateTime.ofInstant(event.getStartDateTime(), MEXICO_ZONE);
            LocalDateTime eventEndDT = LocalDateTime.ofInstant(event.getEndDateTime(), MEXICO_ZONE);
            
            // Ajustar horarios del evento al día actual
            LocalTime eventStart = eventStartDT.toLocalDate().equals(date) 
                ? eventStartDT.toLocalTime() 
                : LocalTime.MIN; // Si empieza antes, ocupa desde medianoche
                
            LocalTime eventEnd = eventEndDT.toLocalDate().equals(date)
                ? eventEndDT.toLocalTime()
                : LocalTime.MAX; // Si termina después, ocupa hasta medianoche
            
            // Limitar al rango permitido
            if (eventStart.isBefore(allowedStart)) eventStart = allowedStart;
            if (eventEnd.isAfter(allowedEnd)) eventEnd = allowedEnd;
            
            // Si hay espacio antes del evento
            if (currentTime.isBefore(eventStart)) {
                freeSlots.add(Map.of(
                    "start", currentTime.format(TIME_FORMATTER),
                    "end", eventStart.format(TIME_FORMATTER)
                ));
            }
            
            // Mover el tiempo actual al final del evento
            if (eventEnd.isAfter(currentTime)) {
                currentTime = eventEnd;
            }
        }
        
        // Si queda tiempo después del último evento
        if (currentTime.isBefore(allowedEnd)) {
            freeSlots.add(Map.of(
                "start", currentTime.format(TIME_FORMATTER),
                "end", allowedEnd.format(TIME_FORMATTER)
            ));
        }
        
        return freeSlots;
    }

    /**
     * Encuentra disponibilidad mutua entre dos usuarios
     * CU29 - Retorna SOLO los espacios donde AMBOS usuarios están disponibles
     * Considera horarios permitidos, eventos de la app y eventos personales (externos)
     */
    public Map<String, Object> findMutualAvailability(Long userId1, Long userId2, int daysAhead) {
        System.out.println("🔎 Calculando disponibilidad mutua entre usuarios " + userId1 + " y " + userId2);
        
        Map<String, Object> user1Availability = calculateAvailableSlots(userId1, daysAhead);
        Map<String, Object> user2Availability = calculateAvailableSlots(userId2, daysAhead);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots1 = (List<Map<String, Object>>) user1Availability.get("availableSlots");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots2 = (List<Map<String, Object>>) user2Availability.get("availableSlots");
        
        List<Map<String, Object>> mutualSlots = new ArrayList<>();
        int totalMutualMinutes = 0;
        
        // Encontrar días en común
        for (Map<String, Object> day1 : slots1) {
            String date1 = (String) day1.get("date");
            
            for (Map<String, Object> day2 : slots2) {
                String date2 = (String) day2.get("date");
                
                if (date1.equals(date2)) {
                    // Mismo día, encontrar slots que se solapan
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> freeSlots1 = (List<Map<String, String>>) day1.get("freeSlots");
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> freeSlots2 = (List<Map<String, String>>) day2.get("freeSlots");
                    
                    List<Map<String, String>> overlappingSlots = findOverlappingSlots(freeSlots1, freeSlots2);
                    
                    if (!overlappingSlots.isEmpty()) {
                        int dayMutualMinutes = calculateTotalMinutes(overlappingSlots);
                        totalMutualMinutes += dayMutualMinutes;
                        
                        mutualSlots.add(Map.of(
                            "date", date1,
                            "dayOfWeek", day1.get("dayOfWeek"),
                            "mutualFreeSlots", overlappingSlots,
                            "totalMutualMinutes", dayMutualMinutes
                        ));
                    }
                }
            }
        }
        
        System.out.println("✅ Disponibilidad mutua: " + mutualSlots.size() + " días con " + 
                         totalMutualMinutes + " minutos totales");
        
        return Map.of(
            "user1Id", userId1,
            "user2Id", userId2,
            "mutualAvailability", mutualSlots,
            "totalDaysWithMutualAvailability", mutualSlots.size(),
            "totalMutualMinutes", totalMutualMinutes
        );
    }

    /**
     * Encuentra los slots que se solapan entre dos listas de slots
     */
    private List<Map<String, String>> findOverlappingSlots(
        List<Map<String, String>> slots1,
        List<Map<String, String>> slots2
    ) {
        List<Map<String, String>> overlapping = new ArrayList<>();
        
        for (Map<String, String> slot1 : slots1) {
            LocalTime start1 = LocalTime.parse(slot1.get("start"), TIME_FORMATTER);
            LocalTime end1 = LocalTime.parse(slot1.get("end"), TIME_FORMATTER);
            
            for (Map<String, String> slot2 : slots2) {
                LocalTime start2 = LocalTime.parse(slot2.get("start"), TIME_FORMATTER);
                LocalTime end2 = LocalTime.parse(slot2.get("end"), TIME_FORMATTER);
                
                // Calcular solapamiento
                LocalTime overlapStart = start1.isAfter(start2) ? start1 : start2;
                LocalTime overlapEnd = end1.isBefore(end2) ? end1 : end2;
                
                if (overlapStart.isBefore(overlapEnd)) {
                    overlapping.add(Map.of(
                        "start", overlapStart.format(TIME_FORMATTER),
                        "end", overlapEnd.format(TIME_FORMATTER)
                    ));
                }
            }
        }
        
        return overlapping;
    }

    /**
     * Guarda la configuración de horarios de un usuario
     */
    @org.springframework.transaction.annotation.Transactional
    public List<AvailabilityScheduleDTO> saveUserSchedule(Long userId, List<AvailabilitySchedule> schedules) {
        System.out.println("💾 Eliminando horarios anteriores del usuario: " + userId);
        // Eliminar configuración anterior
        availabilityScheduleRepository.deleteByUserId(userId);
        availabilityScheduleRepository.flush(); // Forzar la ejecución del DELETE antes del INSERT
        
        System.out.println("✅ Guardando " + schedules.size() + " nuevos horarios...");
        
        // Log detallado de cada schedule antes de guardar
        for (AvailabilitySchedule sched : schedules) {
            System.out.println("  📋 Schedule: day=" + sched.getDayOfWeek() + 
                " enabled=" + sched.getIsEnabled() +
                " start=" + sched.getStartTime() + 
                " end=" + sched.getEndTime() +
                " isAfter=" + sched.getEndTime().isAfter(sched.getStartTime()));
        }
        
        // Guardar nueva configuración
        List<AvailabilitySchedule> saved = availabilityScheduleRepository.saveAll(schedules);
        
        System.out.println("✅ Horarios guardados correctamente en BD");
        return saved.stream()
            .map(AvailabilityScheduleDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * Obtiene la configuración de horarios de un usuario
     */
    public List<AvailabilityScheduleDTO> getUserSchedule(Long userId) {
        List<AvailabilitySchedule> schedules = availabilityScheduleRepository.findByUserId(userId);
        
        return schedules.stream()
            .map(AvailabilityScheduleDTO::fromEntity)
            .collect(Collectors.toList());
    }
    
    /**
     * Expande eventos recurrentes según sus reglas de recurrencia (rrule)
     * Genera ocurrencias individuales para eventos que se repiten
     */
    private List<ExternalEvent> expandRecurringEvents(
        List<ExternalEvent> baseEvents, 
        Instant rangeStart, 
        Instant rangeEnd
    ) {
        List<ExternalEvent> expandedEvents = new ArrayList<>();
        
        for (ExternalEvent baseEvent : baseEvents) {
            // Si no es recurrente, agregar tal cual
            if (baseEvent.getRecurrenceRule() == null || baseEvent.getRecurrenceRule().isEmpty()) {
                expandedEvents.add(baseEvent);
                continue;
            }
            
            // Expandir evento recurrente
            try {
                List<ExternalEvent> occurrences = generateRecurrenceOccurrences(baseEvent, rangeStart, rangeEnd);
                expandedEvents.addAll(occurrences);
            } catch (Exception e) {
                System.err.println("⚠️ Error expandiendo recurrencia para evento " + baseEvent.getTitle() + ": " + e.getMessage());
                // En caso de error, agregar el evento base
                expandedEvents.add(baseEvent);
            }
        }
        
        return expandedEvents;
    }
    
    /**
     * Genera ocurrencias individuales de un evento recurrente
     */
    private List<ExternalEvent> generateRecurrenceOccurrences(
        ExternalEvent baseEvent,
        Instant rangeStart,
        Instant rangeEnd
    ) {
        List<ExternalEvent> occurrences = new ArrayList<>();
        
        Instant eventStart = baseEvent.getRruleDtstartUtc() != null
            ? baseEvent.getRruleDtstartUtc()
            : baseEvent.getStartDatetime();
        Duration eventDuration = Duration.between(baseEvent.getStartDatetime(), baseEvent.getEndDatetime());

        // Se empieza antes del rango para no perder la ocurrencia que cruza su inicio (p. ej. dormir).
        List<Instant> starts = RecurrenceExpander.occurrences(
            eventStart,
            baseEvent.getRecurrenceRule(),
            baseEvent.getRruleUntilUtc(),
            baseEvent.getRruleCount(),
            rangeStart.minus(eventDuration),
            rangeEnd,
            MEXICO_ZONE
        );
        for (Instant start : starts) {
            occurrences.add(createOccurrence(baseEvent, start, start.plus(eventDuration)));
        }
        return occurrences;
    }
    
    /**
     * Crea una ocurrencia individual de un evento recurrente
     */
    private ExternalEvent createOccurrence(ExternalEvent base, Instant start, Instant end) {
        ExternalEvent occurrence = new ExternalEvent();
        occurrence.setExternalCalendarId(base.getExternalCalendarId());
        occurrence.setDeviceEventId(base.getDeviceEventId() + "_" + start.toEpochMilli()); // ID único
        occurrence.setTitle(base.getTitle());
        occurrence.setStartDatetime(start);
        occurrence.setEndDatetime(end);
        occurrence.setStartTimezone(base.getStartTimezone());
        occurrence.setEndTimezone(base.getEndTimezone());
        occurrence.setIsAllDay(base.getIsAllDay());
        occurrence.setLocation(base.getLocation());
        occurrence.setDescription(base.getDescription());
        occurrence.setIsExternal(base.getIsExternal());
        occurrence.setVisibility(base.getVisibility());
        occurrence.setStatus(base.getStatus());
        occurrence.setRecurrenceRule(null); // Las ocurrencias no son recurrentes
        return occurrence;
    }
}
