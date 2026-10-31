package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nexus.dto.CopyPlanDTO;
import com.nexus.dto.CopyPlanDTO.CopyItem;
import com.nexus.dto.CopyPlanDTO.CopyRemoval;
import com.nexus.entity.Event;
import com.nexus.entity.EventStatus;
import com.nexus.entity.ExternalCalendar;
import com.nexus.entity.ExternalEvent;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.ExternalEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** [sync-calendario] U2+U5 - RF-22: que crear, cambiar o borrar en el calendario destino. */
@ExtendWith(MockitoExtension.class)
class CalendarCopyPlannerTest {

    private static final long ME = 13L;
    private static final long TARGET = 1L;
    private static final long OTHER = 2L;
    private static final Instant NOW = Instant.parse("2026-10-31T18:00:00Z");

    @Mock private ExternalCalendarRepository calendars;
    @Mock private ExternalEventRepository externalEvents;
    @Mock private EventRepository events;
    @InjectMocks private CalendarCopyPlanner planner;

    private final List<Event> nexus = new ArrayList<>();
    private final List<ExternalEvent> copies = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(calendars.findExportTarget(ME)).thenReturn(Optional.of(calendar(TARGET, "dev-cal-1")));
        lenient().when(calendars.findByUserIdAndIsActiveTrue(ME))
                .thenReturn(List.of(calendar(TARGET, "dev-cal-1"), calendar(OTHER, "dev-cal-2")));
        lenient().when(events.findByUserIdAndNotDeleted(ME)).thenReturn(nexus);
        lenient().when(externalEvents.findCopies(anyList())).thenReturn(copies);
    }

    private static ExternalCalendar calendar(long id, String deviceId) {
        ExternalCalendar c = new ExternalCalendar();
        c.setId(id);
        c.setUserId(ME);
        c.setDeviceCalendarId(deviceId);
        return c;
    }

    private Event event(long id, EventStatus status, Instant start) {
        Event e = new Event();
        e.setId(id);
        e.setTitle("Cena " + id);
        e.setStartDateTime(start);
        e.setEndDateTime(start.plus(Duration.ofHours(2)));
        e.setStatus(status);
        e.setIsRecurring(false);
        nexus.add(e);
        return e;
    }

    private Event confirmed(long id) {
        return event(id, EventStatus.CONFIRMED, NOW.plus(Duration.ofDays(2)));
    }

    private ExternalEvent copyOf(Event e, long calendarId, String deviceEventId, String signature) {
        ExternalEvent c = new ExternalEvent();
        c.setExternalCalendarId(calendarId);
        c.setDeviceEventId(deviceEventId);
        c.setAppEventId(e.getId());
        c.setExportSignature(signature);
        copies.add(c);
        return c;
    }

    private CopyPlanDTO plan() {
        return planner.planFor(ME, NOW).orElseThrow();
    }

    @Test
    @DisplayName("sin calendario destino no hay plan")
    void noTarget() {
        when(calendars.findExportTarget(ME)).thenReturn(Optional.empty());

        assertThat(planner.planFor(ME, NOW)).isEmpty();
    }

    @Test
    @DisplayName("un evento confirmado sin copia se crea, con sus datos y su repeticion")
    void createsMissingCopy() {
        Event e = confirmed(21L);
        e.setLocation("Coyoacán");
        e.setIsRecurring(true);
        e.setRecurrencePattern("FREQ=WEEKLY;INTERVAL=1;BYDAY=SU");

        CopyPlanDTO plan = plan();

        assertThat(plan.externalCalendarId()).isEqualTo(TARGET);
        assertThat(plan.deviceCalendarId()).isEqualTo("dev-cal-1");
        assertThat(plan.create()).singleElement().satisfies(item -> {
            assertThat(item.appEventId()).isEqualTo(21L);
            assertThat(item.deviceEventId()).isNull();
            assertThat(item.title()).isEqualTo("Cena 21");
            assertThat(item.location()).isEqualTo("Coyoacán");
            assertThat(item.recurrencePattern()).isEqualTo("FREQ=WEEKLY;INTERVAL=1;BYDAY=SU");
            assertThat(item.allDay()).isFalse();
        });
        assertThat(plan.update()).isEmpty();
        assertThat(plan.remove()).isEmpty();
    }

    @Test
    @DisplayName("una copia con la misma firma no se toca; va en keep para revisar que siga en el telefono (R2)")
    void upToDateCopy() {
        Event e = confirmed(21L);
        copyOf(e, TARGET, "dev-21", CalendarCopyPlanner.signatureOf(e));

        CopyPlanDTO plan = plan();

        assertThat(plan.create()).isEmpty();
        assertThat(plan.update()).isEmpty();
        assertThat(plan.remove()).isEmpty();
        assertThat(plan.keep()).extracting(CopyItem::appEventId, CopyItem::deviceEventId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(21L, "dev-21"));
    }

    @Test
    @DisplayName("una copia con otra firma, o sin firma (R1), se actualiza en su mismo evento del telefono")
    void staleCopyIsUpdated() {
        Event changed = confirmed(21L);
        copyOf(changed, TARGET, "dev-21", "firma-vieja");
        Event old = confirmed(22L);
        copyOf(old, TARGET, "dev-22", null);

        assertThat(plan().update()).extracting(CopyItem::appEventId, CopyItem::deviceEventId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(21L, "dev-21"),
                        org.assertj.core.groups.Tuple.tuple(22L, "dev-22"));
    }

    @Test
    @DisplayName("rechazado, cancelado, pendiente o borrado: su copia se quita")
    void notConfirmedIsRemoved() {
        copyOf(event(31L, EventStatus.REJECTED, NOW.plus(Duration.ofDays(1))), TARGET, "dev-31", "x");
        copyOf(event(32L, EventStatus.CANCELLED, NOW.plus(Duration.ofDays(1))), TARGET, "dev-32", "x");
        copyOf(event(33L, EventStatus.PENDING, NOW.plus(Duration.ofDays(1))), TARGET, "dev-33", "x");
        Event deleted = new Event();
        deleted.setId(34L);
        copyOf(deleted, TARGET, "dev-34", "x"); // no viene en findByUserIdAndNotDeleted

        CopyPlanDTO plan = plan();

        assertThat(plan.remove()).extracting(CopyRemoval::deviceEventId)
                .containsExactlyInAnyOrder("dev-31", "dev-32", "dev-33", "dev-34");
        assertThat(plan.create()).isEmpty();
    }

    @Test
    @DisplayName("un evento pendiente no se copia (R12)")
    void pendingIsNotCopied() {
        event(41L, EventStatus.PENDING, NOW.plus(Duration.ofDays(1)));

        assertThat(plan().create()).isEmpty();
    }

    @Test
    @DisplayName("lo que termino hace mas de 30 dias no se copia ni se borra; una serie sin terminar si se copia")
    void window() {
        Event old = event(51L, EventStatus.CONFIRMED, NOW.minus(Duration.ofDays(40)));
        copyOf(old, TARGET, "dev-51", "firma-vieja");
        event(52L, EventStatus.CONFIRMED, NOW.minus(Duration.ofDays(40)));
        Event series = event(53L, EventStatus.CONFIRMED, NOW.minus(Duration.ofDays(90)));
        series.setIsRecurring(true);
        series.setRecurrencePattern("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO");
        Event finished = event(54L, EventStatus.CONFIRMED, NOW.minus(Duration.ofDays(90)));
        finished.setIsRecurring(true);
        finished.setRecurrencePattern("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO;UNTIL=20260801");

        CopyPlanDTO plan = plan();

        assertThat(plan.create()).extracting(CopyItem::appEventId).containsExactly(53L);
        assertThat(plan.update()).isEmpty();
        assertThat(plan.remove()).isEmpty();
    }

    @Test
    @DisplayName("una copia en otro calendario se quita y el evento se crea en el destino (R1)")
    void copyInAnotherCalendarMoves() {
        Event e = confirmed(61L);
        copyOf(e, OTHER, "dev-61", CalendarCopyPlanner.signatureOf(e));

        CopyPlanDTO plan = plan();

        assertThat(plan.remove()).singleElement().satisfies(r -> {
            assertThat(r.externalCalendarId()).isEqualTo(OTHER);
            assertThat(r.deviceEventId()).isEqualTo("dev-61");
        });
        assertThat(plan.create()).extracting(CopyItem::appEventId).containsExactly(61L);
    }

    @Test
    @DisplayName("dos copias del mismo evento en el destino: se queda una")
    void duplicateCopy() {
        Event e = confirmed(71L);
        copyOf(e, TARGET, "dev-71a", CalendarCopyPlanner.signatureOf(e));
        copyOf(e, TARGET, "dev-71b", CalendarCopyPlanner.signatureOf(e));

        CopyPlanDTO plan = plan();

        assertThat(plan.remove()).extracting(CopyRemoval::deviceEventId).containsExactly("dev-71b");
        assertThat(plan.create()).isEmpty();
    }

    @Test
    @DisplayName("un evento de todo el dia se marca como de todo el dia (R5)")
    void allDay() {
        Event e = event(81L, EventStatus.CONFIRMED, Instant.parse("2026-11-03T00:00:00Z"));
        e.setEndDateTime(Instant.parse("2026-11-03T23:59:59.999Z"));

        assertThat(plan().create()).singleElement().extracting(CopyItem::allDay).isEqualTo(true);
    }

    @Test
    @DisplayName("la firma cambia con titulo, inicio, fin o repeticion; no con segundos ni espacios")
    void signature() {
        Event a = confirmed(91L);
        String base = CalendarCopyPlanner.signatureOf(a);

        a.setTitle("  Cena   91 ");
        a.setStartDateTime(a.getStartDateTime().plusSeconds(30));
        assertThat(CalendarCopyPlanner.signatureOf(a)).isEqualTo(base);

        a.setTitle("Cine");
        assertThat(CalendarCopyPlanner.signatureOf(a)).isNotEqualTo(base);
        a.setTitle("Cena 91");
        a.setEndDateTime(a.getEndDateTime().plus(Duration.ofHours(1)));
        assertThat(CalendarCopyPlanner.signatureOf(a)).isNotEqualTo(base);
        a.setEndDateTime(a.getEndDateTime().minus(Duration.ofHours(1)));
        a.setStartDateTime(a.getStartDateTime().plus(Duration.ofHours(1)));
        assertThat(CalendarCopyPlanner.signatureOf(a)).isNotEqualTo(base);
        a.setStartDateTime(a.getStartDateTime().minus(Duration.ofHours(1)));
        a.setRecurrencePattern("FREQ=DAILY;INTERVAL=1");
        assertThat(CalendarCopyPlanner.signatureOf(a)).isNotEqualTo(base);
    }
}
