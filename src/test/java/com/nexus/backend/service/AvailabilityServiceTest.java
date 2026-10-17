package com.nexus.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nexus.entity.AvailabilitySchedule;
import com.nexus.entity.ExternalCalendar;
import com.nexus.entity.ExternalEvent;
import com.nexus.repository.AvailabilityScheduleRepository;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.ExternalEventRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CU28 - Horarios libres de una persona con eventos de su calendario del telefono. El calculo usa
 * "hoy" real, asi que los eventos se arman relativos a hoy (CDMX).
 */
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    private static final ZoneId CDMX = ZoneId.of("America/Mexico_City");
    private static final long USER = 13L;

    @Mock private AvailabilityScheduleRepository schedules;
    @Mock private EventRepository events;
    @Mock private ExternalCalendarRepository calendars;
    @Mock private ExternalEventRepository externalEvents;
    @InjectMocks private AvailabilityService service;

    private final LocalDate today = LocalDate.now(CDMX);

    @BeforeEach
    void setUp() {
        List<AvailabilitySchedule> everyDay = Arrays.stream(AvailabilitySchedule.DayOfWeek.values())
                .map(d -> AvailabilitySchedule.builder()
                        .dayOfWeek(d).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(22, 0)).isEnabled(true).build())
                .toList();
        when(schedules.findEnabledByUserId(USER)).thenReturn(everyDay);
        when(events.findByUserIdAndDateRange(eq(USER), any(), any())).thenReturn(List.of());
        ExternalCalendar calendar = new ExternalCalendar();
        calendar.setId(1L);
        when(calendars.findByUserId(USER)).thenReturn(List.of(calendar));
        when(calendars.findByUserIdAndIsActiveTrue(USER)).thenReturn(List.of(calendar));
    }

    private static ExternalEvent event(String title, Instant start, Instant end, boolean allDay, String rrule) {
        ExternalEvent e = new ExternalEvent();
        e.setTitle(title);
        e.setStartDatetime(start);
        e.setEndDatetime(end);
        e.setIsAllDay(allDay);
        e.setRecurrenceRule(rrule);
        e.setRruleDtstartUtc(rrule != null ? start : null);
        return e;
    }

    private Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(CDMX).toInstant();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> freeSlotsOn(LocalDate day) {
        List<Map<String, Object>> days = (List<Map<String, Object>>) service.calculateAvailableSlots(USER, 7)
                .get("availableSlots");
        return days.stream()
                .filter(d -> day.toString().equals(d.get("date")))
                .findFirst()
                .map(d -> (List<Map<String, String>>) d.get("freeSlots"))
                .orElse(List.of());
    }

    @Test
    @DisplayName("un evento de lunes a viernes ocupa cada dia habil, no solo un dia a la semana")
    void weekdaysAreBusy() {
        // Empezo hace 4 semanas, de 09:00 a 19:00 CDMX, todos los dias de la semana
        LocalDate firstDay = today.minusWeeks(4);
        when(externalEvents.findEventsByCalendarsAndDateRange(anyList(), any(), any())).thenReturn(List.of(
                event("Trabajo", at(firstDay, 9), at(firstDay, 19), false, "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR,SA,SU")));

        LocalDate inThreeDays = today.plusDays(3);
        assertThat(freeSlotsOn(inThreeDays)).containsExactly(Map.of("start", "19:00", "end", "22:00"));
    }

    @Test
    @DisplayName("los eventos de todo el dia no quitan horarios libres")
    void allDayEventsDoNotBlock() {
        LocalDate tomorrow = today.plusDays(1);
        Instant midnightUtc = tomorrow.atStartOfDay(ZoneId.of("UTC")).toInstant();
        when(externalEvents.findEventsByCalendarsAndDateRange(anyList(), any(), any())).thenReturn(List.of(
                event("Mesiversario", midnightUtc, midnightUtc.plusSeconds(86_400), true, null)));

        assertThat(freeSlotsOn(today)).containsExactly(Map.of("start", "09:00", "end", "22:00"));
        assertThat(freeSlotsOn(tomorrow)).containsExactly(Map.of("start", "09:00", "end", "22:00"));
    }
}
