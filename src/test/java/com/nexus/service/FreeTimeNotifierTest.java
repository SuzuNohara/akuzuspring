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
    @DisplayName("cuenta solo los de 1 hora o mas que no han terminado y elige el mas cercano")
    void summaryCountsLongEnoughAndPicksTheNearest() {
        Optional<FreeTimeSummary> s = FreeTimeNotifier.summaryOf(mutual(
                day("2026-11-07", "10:00-20:00"),
                day("2026-11-02", "07:00-08:30", "18:00-18:30", "19:00-22:00"),
                day("2026-11-04", "19:00-20:00")), 60, NOW);

        assertThat(s).isPresent();
        assertThat(s.get().count()).isEqualTo(3);
        assertThat(s.get().first().date()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(s.get().first().start()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    @DisplayName("sin horarios de 1 hora o mas no hay resumen")
    void noLongEnoughSlotMeansNothing() {
        assertThat(FreeTimeNotifier.summaryOf(mutual(day("2026-11-03", "18:00-18:45")), 60, NOW)).isEmpty();
        assertThat(FreeTimeNotifier.summaryOf(Map.of(), 60, NOW)).isEmpty();
    }

    @Test
    @DisplayName("con 4 horarios les avisa a los dos, con el mas cercano")
    void notifiesBothWithTheNearest() {
        given(links.findAllActive()).willReturn(List.of(link(1, 4, 9)));
        given(availability.findMutualAvailability(4L, 9L, 7)).willReturn(mutual(
                day("2026-11-07", "10:00-20:00", "21:00-23:00"), day("2026-11-08", "10:00-12:00", "13:00-15:00")));

        notifier(true).weekly();

        for (long member : new long[] {4, 9}) {
            verify(notifications).notify(eq(member), eq(null), eq(NoticeTypes.FREE_TIME),
                    eq("Tienen 4 horarios libres juntos"), eq("El más cercano: sábado de 10:00 a 20:00."),
                    eq(Map.of("freeTogether", "1")));
        }
    }

    @Test
    @DisplayName("con uno solo dice 'horario libre'")
    void oneSlotIsSingular() {
        given(links.findAllActive()).willReturn(List.of(link(1, 4, 9)));
        given(availability.findMutualAvailability(4L, 9L, 7)).willReturn(mutual(day("2026-11-05", "17:00-19:00")));

        notifier(true).weekly();

        verify(notifications).notify(eq(4L), eq(null), eq(NoticeTypes.FREE_TIME), eq("Tienen 1 horario libre juntos"),
                eq("El más cercano: jueves de 17:00 a 19:00."), any());
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
}
