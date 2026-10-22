package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nexus.entity.Activity;
import com.nexus.exception.AgentUnavailableException;
import com.nexus.integration.AgentTestProperties;
import com.nexus.integration.ollama.OllamaClient;
import com.nexus.integration.qdrant.QdrantClient;
import com.nexus.integration.qdrant.QdrantFilter;
import com.nexus.integration.qdrant.QdrantHit;
import com.nexus.model.IdeaCategory;
import com.nexus.model.Scope;
import com.nexus.repository.ActivityRepository;
import com.nexus.service.ProximityFilter.Coordinates;
import com.nexus.service.recommendation.agent.QueryText;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** [t06b-agente-java] U7 - Busqueda de candidatas en Qdrant con respaldo del catalogo. */
class VectorCandidateSourceTest {

    // Sabado 17-oct-2026, 19:00-21:00 en CDMX => NIGHT, franja de 120 min
    private static final Instant START = Instant.parse("2026-10-18T01:00:00Z");
    private static final Instant END = Instant.parse("2026-10-18T03:00:00Z");
    private static final Coordinates ZOCALO = new Coordinates(19.4326, -99.1332);
    private static final String ALIAS = "nexus_catalog_active";

    private final ActivityRepository activities = mock(ActivityRepository.class);
    private final OllamaClient ollama = mock(OllamaClient.class);
    private final QdrantClient qdrant = mock(QdrantClient.class);
    private final CatalogCandidateSource catalog = mock(CatalogCandidateSource.class);
    private final Selection fromCatalog =
            new Selection(List.of(), EnumSet.noneOf(RelaxedFilter.class), SelectionSource.CATALOG);

    private VectorCandidateSource source(boolean enabled) {
        return new VectorCandidateSource(activities, ollama, qdrant, catalog, AgentTestProperties.with(enabled, true));
    }

    @BeforeEach
    void setUp() {
        lenient().when(ollama.embed(anyString())).thenReturn(new float[] {0.5f});
        lenient().when(qdrant.count(eq(ALIAS), any())).thenReturn(10L);
        lenient().when(catalog.select(any(), any(), any(), anyInt())).thenReturn(fromCatalog);
        lenient().when(activities.findAllById(any())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return ids.stream().filter(id -> id < 70).map(id -> act(id, "HOME")).toList();
        });
    }

    private static Activity act(long id, String scope) {
        return Activity.builder()
                .id(id).title("Plan " + id).activityType("BOARD_GAME").locationScope(scope)
                .durationMin(60).costMxnPp(100).dayparts("ANY").rationale("r").build();
    }

    private static RecommendationQuery query(Scope scope, IdeaCategory category) {
        return new RecommendationQuery(scope, 500, category, START, END, List.of(ZOCALO));
    }

    private static QdrantHit home(long activityId, double score) {
        return new QdrantHit("h" + activityId, score, Map.of("activity_id", (int) activityId));
    }

    private static QdrantHit city(long activityId, long placeId, double lat, double lon, double score) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("activity_id", (int) activityId);
        payload.put("place_id", (int) placeId);
        payload.put("place_name", "Lugar " + placeId);
        payload.put("borough", "Coyoacán");
        payload.put("location", Map.of("lat", lat, "lon", lon));
        return new QdrantHit("c" + placeId, score, payload);
    }

    private void givenHits(QdrantHit... hits) {
        when(qdrant.searchGroups(eq(ALIAS), any(), any(), eq("activity_id"), eq(30))).thenReturn(List.of(hits));
    }

    @Test
    @DisplayName("U7.1: should embed QueryText without the nomic search prefix")
    void shouldEmbedQueryTextWithoutTheNomicSearchPrefix() {
        givenHits(home(1, 0.9));
        CoupleContext ctx = CoupleContext.empty();
        RecommendationQuery q = query(Scope.HOME, IdeaCategory.SOCIAL);

        source(true).select(q, ctx, Set.of(), 5);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(ollama).embed(text.capture());
        assertThat(text.getValue()).isEqualTo(QueryText.of(q, ctx)).doesNotStartWith("search_query:");
    }

    @Test
    @DisplayName("U7.2: should move to the next step when the first one counts zero and mark it relaxed")
    void shouldMoveToTheNextStepWhenTheFirstOneCountsZero() {
        when(qdrant.count(eq(ALIAS), any())).thenReturn(0L).thenReturn(4L);
        givenHits(home(1, 0.9));

        Selection s = source(true).select(query(Scope.HOME, IdeaCategory.SOCIAL), CoupleContext.empty(), Set.of(), 1);

        assertThat(s.relaxed()).containsExactly(RelaxedFilter.CATEGORY);
        ArgumentCaptor<QdrantFilter> filter = ArgumentCaptor.forClass(QdrantFilter.class);
        verify(qdrant).searchGroups(eq(ALIAS), any(), filter.capture(), eq("activity_id"), eq(30));
        assertThat(filter.getValue().dimensions()).isEmpty();
        assertThat(filter.getValue().within()).containsExactly(ZOCALO);
    }

    @Test
    @DisplayName("U7.3: should turn hits into candidates in Qdrant order with their place and distance")
    void shouldTurnHitsIntoCandidatesInQdrantOrderWithPlaceAndDistance() {
        givenHits(city(3, 30, 19.43, -99.14, 0.95), home(1, 0.90), home(2, 0.80));

        Selection s = source(true).select(query(Scope.ANY, null), CoupleContext.empty(), Set.of(), 3);

        assertThat(s.source()).isEqualTo(SelectionSource.VECTOR);
        assertThat(s.relaxed()).isEmpty();
        assertThat(s.candidates()).extracting(c -> c.activity().getId()).containsExactly(3L, 1L, 2L);
        assertThat(s.candidates()).extracting(Candidate::score).containsExactly(0.95, 0.90, 0.80);
        PlaceChoice place = s.candidates().get(0).place();
        assertThat(place.placeId()).isEqualTo(30L);
        assertThat(place.name()).isEqualTo("Lugar 30");
        assertThat(place.borough()).isEqualTo("Coyoacán");
        assertThat(place.distanceKm()).isBetween(0.5, 1.5);
        assertThat(s.candidates().get(1).place()).isNull();
    }

    @Test
    @DisplayName("U7.4: should ignore ids that no longer exist in MySQL and top up from the catalog")
    void shouldIgnoreMissingIdsAndTopUpFromTheCatalog() {
        givenHits(home(1, 0.9), home(77, 0.8));
        Candidate extra = new Candidate(act(5, "HOME"), null, 0);
        when(catalog.select(any(), any(), eq(Set.of(1L)), eq(1)))
                .thenReturn(new Selection(List.of(extra), EnumSet.noneOf(RelaxedFilter.class), SelectionSource.CATALOG));

        Selection s = source(true).select(query(Scope.HOME, null), CoupleContext.empty(), Set.of(), 2);

        assertThat(s.candidates()).extracting(c -> c.activity().getId()).containsExactly(1L, 5L);
    }

    @Test
    @DisplayName("U7.5: should return at most n, one per activity")
    void shouldReturnAtMostNOnePerActivity() {
        givenHits(city(4, 40, 19.43, -99.14, 0.9), city(4, 41, 19.44, -99.14, 0.85), home(1, 0.8), home(2, 0.7));

        Selection s = source(true).select(query(Scope.ANY, null), CoupleContext.empty(), Set.of(), 2);

        assertThat(s.candidates()).extracting(c -> c.activity().getId()).containsExactly(4L, 1L);
        assertThat(s.candidates().get(0).place().placeId()).isEqualTo(40L);
    }

    @Test
    @DisplayName("U7.6: should fall back to the catalog when Ollama or Qdrant fail")
    void shouldFallBackToTheCatalogWhenOllamaOrQdrantFail() {
        when(ollama.embed(anyString())).thenThrow(new AgentUnavailableException("caido"));

        Selection s = source(true).select(query(Scope.HOME, null), CoupleContext.empty(), Set.of(), 5);

        assertThat(s).isSameAs(fromCatalog);
    }

    @Test
    @DisplayName("U7.6: should fall back to the catalog when no step has offers")
    void shouldFallBackToTheCatalogWhenNoStepHasOffers() {
        when(qdrant.count(eq(ALIAS), any())).thenReturn(0L);

        Selection s = source(true).select(query(Scope.HOME, null), CoupleContext.empty(), Set.of(), 5);

        assertThat(s).isSameAs(fromCatalog);
        verify(qdrant, never()).searchGroups(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("U7.7: should use the catalog without calling Qdrant when the agent is off")
    void shouldUseTheCatalogWithoutCallingQdrantWhenTheAgentIsOff() {
        Selection s = source(false).select(query(Scope.HOME, null), CoupleContext.empty(), Set.of(), 5);

        assertThat(s).isSameAs(fromCatalog);
        verifyNoInteractions(ollama, qdrant);
    }

    @Test
    @DisplayName("U7: should pass the excluded activities to the hard filter")
    void shouldPassTheExcludedActivitiesToTheHardFilter() {
        givenHits(home(1, 0.9));

        source(true).select(query(Scope.HOME, null), CoupleContext.empty(), Set.of(9L), 1);

        ArgumentCaptor<QdrantFilter> filter = ArgumentCaptor.forClass(QdrantFilter.class);
        verify(qdrant).count(eq(ALIAS), filter.capture());
        assertThat(new ArrayList<>(filter.getValue().excludedActivityIds())).containsExactly(9L);
    }
}
