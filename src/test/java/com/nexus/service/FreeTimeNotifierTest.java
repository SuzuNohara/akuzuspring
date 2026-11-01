package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nexus.backend.service.AvailabilityService;
import com.nexus.config.FreeTimeProperties;
import com.nexus.entity.Link;
import com.nexus.entity.User;
import com.nexus.repository.LinkRepository;
import com.nexus.service.FreeTimeNotifier.FreeTimeSummary;
import com.nexus.service.push.NoticeTypes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [avisos-calendario] RF-29 - Aviso semanal de horarios libres juntos. */
class FreeTimeNotifierTest {

    /** Lunes 2 de noviembre de 2026, 9:00 en la CDMX. */
    private static final Instant MONDAY_9 = Instant.parse("2026-11-02T15:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 9, 0);

    private final LinkRepository links = mock(LinkRepository.class);
    private final AvailabilityService availability = mock(AvailabilityService.class);
    private final AppNotificationService notifications = mock(AppNotificationService.class);

    private FreeTimeNotifier notifier(boolean enabled) {
        return new FreeTimeNotifier(new FreeTimeProperties(enabled, "0 0 9 * * MON", 60), links, availability,
                notifications, Clock.fixed(MONDAY_9, ZoneId.of("America/Mexico_City")));
    }

    private static Map<String, Object> day(String date, String... ranges) {
        List<Map<String, String>> slots = new java.util.ArrayList<>();
        for (String r : ranges) {
            String[] p = r.split("-");
            slots.add(Map.of("start", p[0], "end", p[1]));
        }
        return Map.of("date", date, "mutualFreeSlots", slots);
    }

    private static Map<String, Object> mutual(Map<String, Object>... days) {
        return Map.of("mutualAvailability", List.of(days));
    }

    private static Link link(long id, long a, long b) {
        return Link.builder().id(id).initiatorUser(User.builder().id(a).build()).partnerUser(User.builder().id(b).build()).build();
    }

    @Test
    @DisplayName("[libres-juntos] cuenta los horarios limpios y propone el del dia mas cercano")
    void summaryCountsLongEnoughAndPicksTheNearest() {
        Optional<FreeTimeSummary> s = FreeTimeNotifier.summaryOf(mutual(
                day("2026-11-07", "10:00-20:00"),
                day("2026-11-02", "07:00-08:30", "18:00-18:30", "19:00-22:00"),
                day("2026-11-04", "19:00-20:00")), 60, NOW);

        assertThat(s).isPresent();
        // 7 nov: 10-13, 13-19, 19-20; 2 nov: 19-22; 4 nov: 19-20. Lo de las 7:00 ya paso y 18:00-18:30 es corto.
        assertThat(s.get().count()).isEqualTo(5);
        assertThat(s.get().best().date()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(s.get().best().start()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    @DisplayName("sin horarios de 1 hora o mas no hay resumen")
    void noLongEnoughSlotMeansNothing() {
        assertThat(FreeTimeNotifier.summaryOf(mutual(day("2026-11-03", "18:00-18:45")), 60, NOW)).isEmpty();
        assertThat(FreeTimeNotifier.summaryOf(Map.of(), 60, NOW)).isEmpty();
    }

    @Test
    @DisplayName("[libres-juntos] les avisa a los dos con la mejor propuesta, en tono calido")
    void notifiesBothWithTheBestProposal() {
        given(links.findAllActive()).willReturn(List.of(link(1, 4, 9)));
        given(availability.findMutualAvailability(4L, 9L, 7)).willReturn(mutual(
                day("2026-11-07", "10:00-20:00", "21:00-23:00"), day("2026-11-08", "10:00-12:00", "13:00-15:00")));

        notifier(true).weekly();

        for (long member : new long[] {4, 9}) {
            verify(notifications).notify(eq(member), eq(null), eq(NoticeTypes.FREE_TIME),
                    eq("Esta semana tienen tiempo juntos"),
                    eq("El sábado en la tarde tienen 6 h libres. ¿Planeamos algo?"),
                    eq(Map.of("freeTogether", "1")));
        }
    }

    @Test
    @DisplayName("[libres-juntos] hoy y mañana se dicen sin articulo")
    void todayAndTomorrowWithoutArticle() {
        given(links.findAllActive()).willReturn(List.of(link(1, 4, 9)));
        given(availability.findMutualAvailability(4L, 9L, 7)).willReturn(mutual(day("2026-11-03", "17:00-19:00")));

        notifier(true).weekly();

        verify(notifications).notify(eq(4L), eq(null), eq(NoticeTypes.FREE_TIME), eq("Esta semana tienen tiempo juntos"),
                eq("Mañana en la tarde tienen 2 h libres. ¿Planeamos algo?"), any());
    }

    @Test
    @DisplayName("sin horarios libres no se manda nada")
    void noFreeTimeSendsNothing() {
        given(links.findAllActive()).willReturn(List.of(link(1, 4, 9)));
        given(availability.findMutualAvailability(4L, 9L, 7)).willReturn(mutual());

        notifier(true).weekly();

        verify(notifications, never()).notify(anyLong(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("si una pareja falla, a las demas si les llega")
    void oneFailingCoupleDoesNotStopTheOthers() {
        given(links.findAllActive()).willReturn(List.of(link(1, 4, 9), link(2, 5, 6)));
        given(availability.findMutualAvailability(4L, 9L, 7)).willThrow(new IllegalStateException("sin horario"));
        given(availability.findMutualAvailability(5L, 6L, 7)).willReturn(mutual(day("2026-11-05", "17:00-19:00")));

        notifier(true).weekly();

        verify(notifications).notify(eq(5L), any(), eq(NoticeTypes.FREE_TIME), anyString(), anyString(), any());
        verify(notifications).notify(eq(6L), any(), eq(NoticeTypes.FREE_TIME), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("apagado no busca ni manda nada")
    void disabledDoesNothing() {
        notifier(false).weekly();

        verifyNoInteractions(links, availability, notifications);
        verify(availability, never()).findMutualAvailability(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("[ux-ajustes] D2: si el ultimo aviso de la semana es igual, no se repite; si cambia, si llega")
    void sameNoticeOfTheWeekIsNotRepeated() {
        given(links.findAllActive()).willReturn(List.of(link(1, 4, 9)));
        given(availability.findMutualAvailability(4L, 9L, 7)).willReturn(mutual(day("2026-11-03", "17:00-19:00")));
        Instant weekAgo = MONDAY_9.minus(java.time.Duration.ofDays(7));
        given(notifications.lastOf(4L, NoticeTypes.FREE_TIME, weekAgo)).willReturn(Optional.of(
                com.nexus.entity.AppNotification.builder().title("Esta semana tienen tiempo juntos")
                        .body("Mañana en la tarde tienen 2 h libres. ¿Planeamos algo?").build()));
        given(notifications.lastOf(9L, NoticeTypes.FREE_TIME, weekAgo)).willReturn(Optional.of(
                com.nexus.entity.AppNotification.builder().title("Esta semana tienen tiempo juntos")
                        .body("El sábado en la tarde tienen 6 h libres. ¿Planeamos algo?").build()));

        notifier(true).weekly();

        verify(notifications, never()).notify(eq(4L), any(), anyString(), anyString(), anyString(), any());
        verify(notifications).notify(eq(9L), eq(null), eq(NoticeTypes.FREE_TIME), anyString(),
                eq("Mañana en la tarde tienen 2 h libres. ¿Planeamos algo?"), any());
    }
}
