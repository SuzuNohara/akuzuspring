package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.nexus.entity.Event;
import com.nexus.entity.EventStatus;
import com.nexus.entity.ExternalCalendar;
import com.nexus.entity.ExternalEvent;
import com.nexus.repository.EventExceptionRepository;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.ExternalEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [eventos-sin-choques] D1: qué cuenta como ocupado para una persona. */
class BusyTimeServiceTest {

    private static final long USER_ID = 4L;
    private static final Instant FROM = Instant.parse("2026-11-20T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-21T00:00:00Z");
    private static final Instant NOON = Instant.parse("2026-11-20T18:00:00Z");

    private final EventRepository events = mock(EventRepository.class);
    private final EventExceptionRepository exceptions = mock(EventExceptionRepository.class);
    private final ExternalCalendarRepository calendars = mock(ExternalCalendarRepository.class);
    private final ExternalEventRepository externalEvents = mock(ExternalEventRepository.class);
    private final BusyTimeService service = new BusyTimeService(events, exceptions, calendars, externalEvents);

    @BeforeEach
    void noCalendarsByDefault() {
        given(calendars.findByUserIdAndIsActiveTrue(USER_ID)).willReturn(List.of());
        given(events.findActiveRecurringByUserIdStartingBefore(eq(USER_ID), any())).willReturn(List.of());
        given(exceptions.findExceptionDatesByEventId(any())).willReturn(List.of());
    }

    private static Event nexus(long id, EventStatus status, Instant start, Duration length) {
        return Event.builder().id(id).title("Plan").status(status).isRecurring(false)
                .startDateTime(start).endDateTime(start.plus(length)).build();
    }

    private void givenNexusEvents(Event... list) {
        given(events.findByUserIdAndDateRange(USER_ID, FROM, TO)).willReturn(List.of(list));
    }

    private void givenPhoneEvents(ExternalEvent... list) {
        ExternalCalendar cal = new ExternalCalendar();
        cal.setId(70L);
        given(calendars.findByUserIdAndIsActiveTrue(USER_ID)).willReturn(List.of(cal));
        given(externalEvents.findEventsByCalendarsAndDateRange(anyList(), eq(FROM), eq(TO))).willReturn(List.of(list));
    }

    private static ExternalEvent phone(Instant start, Duration length, boolean allDay, Long appEventId) {
        ExternalEvent e = new ExternalEvent();
        e.setTitle("Algo");
        e.setStartDatetime(start);
        e.setEndDatetime(start.plus(length));
        e.setIsAllDay(allDay);
        e.setAppEventId(appEventId);
        return e;
    }

    @Test
    @DisplayName("confirmed and pending Nexus events are busy; rejected and cancelled are not")
    void confirmedAndPendingNexusEventsAreBusy() {
        givenNexusEvents(
                nexus(1, EventStatus.CONFIRMED, NOON, Duration.ofHours(1)),
                nexus(2, EventStatus.PENDING, NOON.plus(Duration.ofHours(2)), Duration.ofHours(1)),
                nexus(3, EventStatus.REJECTED, NOON.plus(Duration.ofHours(4)), Duration.ofHours(1)),
                nexus(4, EventStatus.CANCELLED, NOON.plus(Duration.ofHours(6)), Duration.ofHours(1)));

        List<BusyTimeService.Interval> busy = service.busyIntervals(USER_ID, FROM, TO, null);

        assertThat(busy).extracting(BusyTimeService.Interval::start)
                .containsExactlyInAnyOrder(NOON, NOON.plus(Duration.ofHours(2)));
    }

    @Test
    @DisplayName("the event being edited does not count against itself")
    void theEditedEventIsIgnored() {
        givenNexusEvents(nexus(1, EventStatus.CONFIRMED, NOON, Duration.ofHours(1)));

        assertThat(service.busyIntervals(USER_ID, FROM, TO, 1L)).isEmpty();
    }

    @Test
    @DisplayName("timed phone events are busy; all-day events and copies of Nexus events are not")
    void phoneEventsWithoutAllDayOrCopies() {
        givenNexusEvents();
        givenPhoneEvents(
                phone(NOON, Duration.ofHours(1), false, null),
                phone(FROM, Duration.ofDays(1), true, null),
                phone(NOON.plus(Duration.ofHours(3)), Duration.ofHours(1), false, 99L));

        assertThat(service.busyIntervals(USER_ID, FROM, TO, null))
                .containsExactly(new BusyTimeService.Interval(NOON, NOON.plus(Duration.ofHours(1))));
    }

    @Test
    @DisplayName("recurring Nexus events count on each day they repeat")
    void recurringNexusEventsAreExpanded() {
        givenNexusEvents();
        Event weekly = Event.builder().id(5L).title("Clase").status(EventStatus.CONFIRMED).isRecurring(true)
                .recurrencePattern("DAILY")
                .startDateTime(NOON.minus(Duration.ofDays(3))).endDateTime(NOON.minus(Duration.ofDays(3)).plus(Duration.ofHours(1)))
                .build();
        given(events.findActiveRecurringByUserIdStartingBefore(USER_ID, TO)).willReturn(List.of(weekly));

        assertThat(service.busyIntervals(USER_ID, FROM, TO, null))
                .contains(new BusyTimeService.Interval(NOON, NOON.plus(Duration.ofHours(1))));
    }

    @Test
    @DisplayName("touching edges do not overlap: a plan may start when another ends")
    void touchingEdgesDoNotOverlap() {
        BusyTimeService.Interval a = new BusyTimeService.Interval(NOON, NOON.plus(Duration.ofHours(1)));
        assertThat(a.overlaps(NOON.plus(Duration.ofHours(1)), NOON.plus(Duration.ofHours(2)))).isFalse();
        assertThat(a.overlaps(NOON.plus(Duration.ofMinutes(30)), NOON.plus(Duration.ofHours(2)))).isTrue();
    }
}
