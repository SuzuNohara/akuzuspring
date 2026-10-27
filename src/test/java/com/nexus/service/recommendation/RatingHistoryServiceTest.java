package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nexus.entity.Activity;
import com.nexus.entity.DateRating;
import com.nexus.entity.Event;
import com.nexus.entity.EventStatus;
import com.nexus.entity.Link;
import com.nexus.entity.User;
import com.nexus.model.IdeaCategory;
import com.nexus.model.ReasonSource;
import com.nexus.repository.ActivityRepository;
import com.nexus.repository.DateRatingRepository;
import com.nexus.repository.EventRepository;
import com.nexus.security.AesEncryptionService;
import com.nexus.service.recommendation.RatingHistoryService.RatingHistory;
import com.nexus.service.recommendation.agent.WrittenPick;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** CU43 / RF-42 - Citas evaluadas de la pareja (RN-35) y orden de las opciones por historial. */
class RatingHistoryServiceTest {

    private static final long ANA = 1L;
    private static final long SAM = 2L;
    private static final Instant NOW = Instant.parse("2026-10-26T18:00:00Z");

    private final EventRepository events = mock(EventRepository.class);
    private final DateRatingRepository ratings = mock(DateRatingRepository.class);
    private final ActivityRepository activities = mock(ActivityRepository.class);
    /** "Cifra" el puntaje como el texto mismo: {@code {"score":n}}. */
    private final AesEncryptionService aes = mock(AesEncryptionService.class);

    private final RatingHistoryService service =
            new RatingHistoryService(events, ratings, activities, aes, Clock.fixed(NOW, ZoneOffset.UTC));

    private final Link link = Link.builder().id(7L)
            .initiatorUser(User.builder().id(ANA).build())
            .partnerUser(User.builder().id(SAM).build())
            .build();

    private final List<Event> confirmed = new ArrayList<>();
    private final List<DateRating> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        given(events.findByLinkIdAndStatus(7L, EventStatus.CONFIRMED)).willReturn(confirmed);
        given(ratings.findByEventIdIn(anyCollection())).willAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            return stored.stream().filter(r -> ids.contains(r.getEventId())).toList();
        });
        given(aes.decrypt(anyString())).willAnswer(inv -> inv.getArgument(0));
    }

    /** Cita suelta y confirmada que termino hace un dia. */
    private Event date(long id, String category) {
        Instant end = NOW.minus(Duration.ofDays(1));
        Event e = Event.builder().id(id).title("Cita " + id).link(link)
                .startDateTime(end.minus(Duration.ofHours(2))).endDateTime(end)
                .status(EventStatus.CONFIRMED).isRecurring(false).category(category)
                .build();
        confirmed.add(e);
        return e;
    }

    private void rate(long eventId, long userId, int score) {
        stored.add(DateRating.builder().eventId(eventId).userId(userId).encryptedScore("{\"score\":" + score + "}").build());
    }

    @Nested
    @DisplayName("Conteo de citas evaluadas (RN-35)")
    class Count {

        @Test
        @DisplayName("una cita calificada por uno solo cuenta; una que nadie califico, no")
        void oneRaterIsEnoughNoneIsNot() {
            date(1, "Físico");
            date(2, "Físico");
            rate(1, ANA, 4);

            assertThat(service.historyOf(link).ratedDates()).isEqualTo(1);
        }

        @Test
        @DisplayName("si califican los dos la misma cita, cuenta una vez")
        void bothRatersCountOnce() {
            date(1, "Físico");
            rate(1, ANA, 4);
            rate(1, SAM, 2);

            assertThat(service.historyOf(link).ratedDates()).isEqualTo(1);
        }

        @Test
        @DisplayName("los eventos con repeticion no cuentan aunque tengan calificacion")
        void recurringEventsAreExcluded() {
            date(1, "Físico").setIsRecurring(true);
            rate(1, ANA, 5);

            assertThat(service.historyOf(link).ratedDates()).isZero();
        }

        @Test
        @DisplayName("solo cuentan citas confirmadas, terminadas y vigentes")
        void onlyConfirmedFinishedLiveDates() {
            date(1, "Físico").setStatus(EventStatus.PENDING);
            date(2, "Físico").setEndDateTime(NOW.plus(Duration.ofHours(1)));
            date(3, "Físico").setDeletedAt(NOW);
            date(4, "Físico");
            for (long id = 1; id <= 4; id++) {
                rate(id, ANA, 5);
            }

            assertThat(service.historyOf(link).ratedDates()).isEqualTo(1);
        }

        @Test
        @DisplayName("con 2 citas evaluadas es flujo en frio y no se descifra nada")
        void twoIsCold() {
            date(1, "Físico");
            date(2, "Social");
            rate(1, ANA, 5);
            rate(2, SAM, 1);

            RatingHistory history = service.historyOf(link);

            assertThat(history.ratedDates()).isEqualTo(2);
            assertThat(history.isWarm()).isFalse();
            assertThat(history.averages()).isEmpty();
            verify(aes, never()).decrypt(anyString());
        }

        @Test
        @DisplayName("con 3 citas evaluadas hay historial")
        void threeIsWarm() {
            date(1, "Físico");
            date(2, "Social");
            date(3, "Social");
            rate(1, ANA, 5);
            rate(2, SAM, 1);
            rate(3, ANA, 2);

            RatingHistory history = service.historyOf(link);

            assertThat(history.ratedDates()).isEqualTo(3);
            assertThat(history.isWarm()).isTrue();
        }

        @Test
        @DisplayName("sin citas no consulta calificaciones")
        void noDatesNoRatingQuery() {
            assertThat(service.historyOf(link).ratedDates()).isZero();
            verify(ratings, never()).findByEventIdIn(anyCollection());
        }
    }

    @Nested
    @DisplayName("Promedio por dimension")
    class Average {

        @Test
        @DisplayName("promedia los puntajes de los dos por dimension")
        void averagesBothMembersByCategory() {
            date(1, "Físico");
            date(2, "Físico");
            date(3, "Social");
            rate(1, ANA, 5);
            rate(1, SAM, 4);
            rate(2, ANA, 3);
            rate(3, SAM, 2);

            assertThat(service.historyOf(link).averages())
                    .containsExactlyInAnyOrderEntriesOf(Map.of(IdeaCategory.FISICO, 4.0, IdeaCategory.SOCIAL, 2.0));
        }

        @Test
        @DisplayName("sin categoria en el evento usa la de su actividad; el codigo y sin acentos tambien valen")
        void fallsBackToTheActivity() {
            date(1, null).setActivityId(40L);
            date(2, "FISICO");
            date(3, "fisico");
            date(4, "Algo libre");
            rate(1, ANA, 5);
            rate(2, ANA, 1);
            rate(3, ANA, 3);
            rate(4, ANA, 5);
            given(activities.findAllById(anyCollection()))
                    .willReturn(List.of(Activity.builder().id(40L).activityType("HIKE").build()));

            RatingHistory history = service.historyOf(link);

            assertThat(history.ratedDates()).isEqualTo(4);
            assertThat(history.averages())
                    .containsExactlyInAnyOrderEntriesOf(Map.of(IdeaCategory.AMBIENTAL, 5.0, IdeaCategory.FISICO, 2.0));
        }

        @Test
        @DisplayName("una calificacion ilegible se omite del promedio sin romper la solicitud")
        void unreadableScoreIsSkipped() {
            date(1, "Físico");
            date(2, "Físico");
            date(3, "Físico");
            rate(1, ANA, 4);
            rate(2, ANA, 2);
            stored.add(DateRating.builder().eventId(3L).userId(SAM).encryptedScore("roto").build());
            given(aes.decrypt("roto")).willThrow(new IllegalStateException("llave"));

            assertThat(service.historyOf(link).averages()).containsExactlyEntriesOf(Map.of(IdeaCategory.FISICO, 3.0));
        }
    }

    @Nested
    @DisplayName("Orden por historial")
    class Rank {

        private WrittenPick pick(long id, String type, ReasonSource source) {
            Activity a = Activity.builder().id(id).title("Plan " + id).activityType(type).build();
            return new WrittenPick(new Candidate(a, null, 0), 0, "frase " + id, source);
        }

        private List<Long> ids(List<WrittenPick> picks) {
            return picks.stream().map(p -> p.candidate().activity().getId()).toList();
        }

        /** Orden del compositor: social, fisico, ambiental, social, intelectual, fisico. */
        private List<WrittenPick> composed() {
            return List.of(
                    pick(1, "FILM", ReasonSource.AGENT),
                    pick(2, "SWIM", ReasonSource.AGENT),
                    pick(3, "HIKE", ReasonSource.CATALOG),
                    pick(4, "COFFEE", ReasonSource.CATALOG),
                    pick(5, "READING", ReasonSource.CATALOG),
                    pick(6, "CLIMB", ReasonSource.CATALOG));
        }

        @Test
        @DisplayName("primero la dimension mejor calificada; dentro de ella, el orden del compositor; sin calificar al final")
        void bestRatedCategoriesFirst() {
            RatingHistory history = new RatingHistory(3, Map.of(IdeaCategory.FISICO, 4.5, IdeaCategory.SOCIAL, 3.0));

            assertThat(ids(RatingHistoryService.rankByHistory(composed(), history)))
                    .containsExactly(2L, 6L, 1L, 4L, 3L, 5L);
        }

        @Test
        @DisplayName("con el mismo promedio, la dimension que aparecia antes va primero y no se mezclan")
        void tiesKeepCategoriesTogether() {
            RatingHistory history = new RatingHistory(4, Map.of(IdeaCategory.FISICO, 4.0, IdeaCategory.SOCIAL, 4.0));

            assertThat(ids(RatingHistoryService.rankByHistory(composed(), history)))
                    .containsExactly(1L, 4L, 2L, 6L, 3L, 5L);
        }

        @Test
        @DisplayName("en frio (2 citas) la lista queda igual")
        void coldKeepsTheOrder() {
            RatingHistory history = new RatingHistory(2, Map.of());

            assertThat(ids(RatingHistoryService.rankByHistory(composed(), history)))
                    .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        }

        @Test
        @DisplayName("la frase y su fuente viajan con cada opcion")
        void reasonsTravelWithTheirPick() {
            RatingHistory history = new RatingHistory(3, Map.of(IdeaCategory.INTELECTUAL, 5.0));

            List<WrittenPick> ranked = RatingHistoryService.rankByHistory(composed(), history);

            assertThat(ranked.get(0).reason()).isEqualTo("frase 5");
            assertThat(ranked.get(0).source()).isEqualTo(ReasonSource.CATALOG);
            assertThat(ids(ranked)).containsExactly(5L, 1L, 2L, 3L, 4L, 6L);
        }
    }
}
