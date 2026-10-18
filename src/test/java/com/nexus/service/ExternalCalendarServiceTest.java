package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nexus.dto.ExternalEventDTO;
import com.nexus.entity.ExternalCalendar;
import com.nexus.entity.ExternalEvent;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.ExternalEventRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** RF-23 - La privacidad de un calendario oculta el detalle a la pareja, nunca a su dueña. */
@ExtendWith(MockitoExtension.class)
class ExternalCalendarServiceTest {

    private static final long ME = 13L;
    private static final long PARTNER = 4L;
    private static final Instant FROM = Instant.parse("2026-10-18T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-25T00:00:00Z");

    @Mock private ExternalCalendarRepository calendars;
    @Mock private ExternalEventRepository externalEvents;
    @Mock private EventRepository events;
    @InjectMocks private ExternalCalendarService service;

    private void busyOnlyCalendar(long calendarId, long owner, String title) {
        ExternalCalendar calendar = new ExternalCalendar();
        calendar.setId(calendarId);
        calendar.setUserId(owner);
        calendar.setPrivacyMode(ExternalCalendar.PrivacyMode.BUSY_ONLY);
        ExternalEvent event = new ExternalEvent();
        event.setId(calendarId * 10);
        event.setExternalCalendarId(calendarId);
        event.setTitle(title);
        event.setLocation("Consultorio");
        event.setStartDatetime(FROM.plusSeconds(3600));
        event.setEndDatetime(FROM.plusSeconds(7200));
        event.setIsAllDay(false);
        event.setVisibility(ExternalEvent.Visibility.DEFAULT);
        when(calendars.findByUserIdAndIsActiveTrue(owner)).thenReturn(List.of(calendar));
        when(externalEvents.findEventsByCalendarsAndDateRange(eq(List.of(calendarId)), any(), any()))
                .thenReturn(List.of(event));
    }

    @Test
    @DisplayName("la dueña ve el titulo de sus eventos aunque su calendario sea 'solo ocupado'")
    void ownerSeesOwnDetails() {
        busyOnlyCalendar(1L, ME, "Dentista");
        busyOnlyCalendar(2L, PARTNER, "Junta");

        List<ExternalEventDTO> seen = service.getEventsWithPrivacy(ME, FROM, TO, PARTNER);

        ExternalEventDTO mine = seen.stream().filter(e -> e.getOwnerId() == ME).findFirst().orElseThrow();
        ExternalEventDTO theirs = seen.stream().filter(e -> e.getOwnerId() == PARTNER).findFirst().orElseThrow();
        assertThat(mine.getTitle()).isEqualTo("Dentista");
        assertThat(mine.getLocation()).isEqualTo("Consultorio");
        assertThat(theirs.getTitle()).isEqualTo("Ocupado");
        assertThat(theirs.getLocation()).isNull();
    }
}
