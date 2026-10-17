package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexus.config.AgentProperties;
import com.nexus.entity.Activity;
import com.nexus.exception.AgentUnavailableException;
import com.nexus.integration.AgentTestProperties;
import com.nexus.integration.ollama.OllamaClient;
import com.nexus.integration.qdrant.QdrantClient;
import com.nexus.integration.qdrant.QdrantFilter;
import com.nexus.integration.qdrant.ScoredId;
import com.nexus.model.IdeaCategory;
import com.nexus.model.Scope;
import com.nexus.repository.ActivityPlaceView;
import com.nexus.repository.ActivityRepository;
import com.nexus.repository.PlaceRepository;
import com.nexus.service.ProximityFilter.Coordinates;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** [T-06] U7 con el agente - S1, S2, S4, S9, S10: busqueda en Qdrant con respaldo del catalogo. */
class VectorCandidateSourceTest {

    // Sabado 17-oct-2026, 19:00-21:00 en CDMX => NIGHT, franja de 120 min
    private static final Instant START = Instant.parse("2026-10-18T01:00:00Z");
    private static final Instant END = Instant.parse("2026-10-18T03:00:00Z");
    private static final Coordinates ZOCALO = new Coordinates(19.4326, -99.1332);

    private final ActivityRepository activities = mock(ActivityRepository.class);
    private final PlaceRepository places = mock(PlaceRepository.class);
    private final OllamaClient ollama = mock(OllamaClient.class);
    private final QdrantClient qdrant = mock(QdrantClient.class);
    private final CatalogCandidateSource catalog = mock(CatalogCandidateSource.class);
    private final List<ActivityPlaceView> placeRows = new ArrayList<>();
    private final Selection fromCatalog =
            new Selection(List.of(), EnumSet.noneOf(RelaxedFilter.class), SelectionSource.CATALOG);

    private VectorCandidateSource source(boolean enabled) {
        AgentProperties d = AgentTestProperties.defaults();
        AgentProperties props = new AgentProperties(
                d.ollamaUrl(), d.chatModel(), d.embedModel(), d.qdrantUrl(), d.collection(),
                d.llmTimeout(), d.candidatePool(), d.indexOnStartup(), enabled);
        return new VectorCandidateSource(activities, places, ollama, qdrant, catalog, props);
    }

    @BeforeEach
    void setUp() {
        lenient().when(ollama.embed(anyString())).thenReturn(new float[] {0.5f});
        lenient().when(places.findByActivityIds(anyCollection())).thenReturn(placeRows);
        lenient().when(catalog.select(any(), any(), any(), anyInt())).thenReturn(fromCatalog);
    }

    private static Activity act(long id, String scope) {
        return Activity.builder()
                .id(id).title("Plan " + id).activityType("BOARD_GAME").locationScope(scope)
                .durationMin(60).costMxnPp(100).dayparts("ANY").rationale("r").build();
    }

    private static ActivityPlaceView place(long activityId, double lat, double lon) {
        return new ActivityPlaceView() {
            public Long getActivityId() { return activityId; }
            public Long getPlaceId() { return activityId * 10; }
            public String getName() { return "Lugar"; }
            public String getBorough() { return "Coyoacan"; }
            public Double getLat() { return lat; }
            public Double getLon() { return lon; }
        };
    }

    private static RecommendationQuery query(Scope scope, IdeaCategory category) {
        return new RecommendationQuery(scope, 500, category, START, END, List.of(ZOCALO));
    }

    private void givenHits(long... ids) {
        // Qdrant regresa del mas al menos parecido.
        List<ScoredId> hits = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            hits.add(new ScoredId(ids[i], 0.9 - i * 0.1));
        }
        when(qdrant.search(eq("activities"), any(), any(), anyInt())).thenReturn(hits).thenReturn(List.of());
    }

    @Test
    @DisplayName("S1: con suficientes resultados regresa n en el orden de Qdrant y sin ampliar")
    void returnsHitsInOrder() {
        givenHits(3, 1, 2);
        when(activities.findAllById(any())).thenReturn(List.of(act(1, "HOME"), act(2, "HOME"), act(3, "HOME")));

        Selection s = source(true).select(query(Scope.HOME, IdeaCategory.SOCIAL), CoupleContext.empty(), Set.of(), 3);

        assertThat(s.source()).isEqualTo(SelectionSource.VECTOR);
        assertThat(s.relaxed()).isEmpty();
        assertThat(s.candidates()).extracting(c -> c.activity().getId()).containsExactly(3L, 1L, 2L);
    }

    @Test
    @DisplayName("S2: el filtro de Qdrant lleva alcance, presupuesto, dimension, momento, franja y excluidas")
    void filterCarriesEveryCondition() {
        givenHits(1);
        when(activities.findAllById(any())).thenReturn(List.of(act(1, "HOME")));

        source(true).select(query(Scope.HOME, IdeaCategory.SOCIAL), CoupleContext.empty(), Set.of(9L), 1);

        ArgumentCaptor<QdrantFilter> filter = ArgumentCaptor.forClass(QdrantFilter.class);
        verify(qdrant).search(eq("activities"), any(), filter.capture(), eq(40));
        assertThat(filter.getValue())
                .isEqualTo(new QdrantFilter(Set.of("HOME"), 500, Set.of("SOCIAL"), "NIGHT", 120, Set.of(9L)));
    }

    @Test
    @DisplayName("S4/S11: una actividad en la ciudad sin lugar a 15 km o menos no entra")
    void dropsCityActivitiesTooFar() {
        givenHits(1, 2);
        when(activities.findAllById(any())).thenReturn(List.of(act(1, "CITY"), act(2, "CITY")));
        placeRows.add(place(1, 19.43, -99.14)); // a ~1 km del Zocalo
        placeRows.add(place(2, 19.80, -99.14)); // a ~41 km

        Selection s = source(true).select(query(Scope.CITY, null), CoupleContext.empty(), Set.of(), 1);

        assertThat(s.candidates()).extracting(c -> c.activity().getId()).containsExactly(1L);
        assertThat(s.candidates().get(0).place().placeId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("S10: ignora ids que ya no estan en MySQL y completa con el catalogo")
    void ignoresMissingIdsAndTopsUpFromCatalog() {
        givenHits(1, 77);
        when(activities.findAllById(any())).thenReturn(List.of(act(1, "HOME")));
        Candidate extra = new Candidate(act(5, "HOME"), null, 0);
        when(catalog.select(any(), any(), eq(Set.of(1L)), eq(1)))
                .thenReturn(new Selection(List.of(extra), EnumSet.noneOf(RelaxedFilter.class), SelectionSource.CATALOG));

        Selection s = source(true).select(query(Scope.HOME, null), CoupleContext.empty(), Set.of(), 2);

        assertThat(s.candidates()).extracting(c -> c.activity().getId()).containsExactly(1L, 5L);
        assertThat(s.source()).isEqualTo(SelectionSource.VECTOR);
    }

    @Test
    @DisplayName("S9: si Ollama o Qdrant fallan, todo sale del catalogo")
    void fallsBackToCatalogWhenAgentFails() {
        when(ollama.embed(anyString())).thenThrow(new AgentUnavailableException("caido"));

        Selection s = source(true).select(query(Scope.HOME, null), CoupleContext.empty(), Set.of(), 5);

        assertThat(s).isSameAs(fromCatalog);
    }

    @Test
    @DisplayName("con el agente apagado ni siquiera llama a Ollama")
    void usesCatalogWhenDisabled() {
        Selection s = source(false).select(query(Scope.HOME, null), CoupleContext.empty(), Set.of(), 5);

        assertThat(s).isSameAs(fromCatalog);
        verify(ollama, never()).embed(anyString());
    }

    @Test
    @DisplayName("la consulta usa el prefijo de busqueda, las dimensiones y el nivel combinado")
    void queryTextUsesDimensionsAndCombinedMood() {
        CoupleContext ctx = new CoupleContext(Map.of(IdeaCategory.FISICO, 3), MoodLevel.BAJO, MoodLevel.BAJO);

        String text = VectorCandidateSource.queryText(query(Scope.HOME, null), ctx);

        assertThat(text).startsWith("search_query: ").contains("Físico", "en casa", "noche", "tranquilo");
    }
}
