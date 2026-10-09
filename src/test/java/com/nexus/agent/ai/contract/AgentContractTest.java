package com.nexus.agent.ai.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.agent.ai.catalog.BankCategory;
import com.nexus.agent.ai.catalog.CatalogEntry;
import com.nexus.agent.ai.catalog.LocationScope;
import com.nexus.agent.ai.catalog.PriceBand;
import com.nexus.agent.ai.catalog.WeatherOk;
import com.nexus.agent.ai.catalog.WellnessDimension;
import com.nexus.agent.ai.compose.Composition;
import com.nexus.agent.ai.retrieval.CandidateActivity;
import com.nexus.agent.ai.retrieval.PreferenceSignal;
import com.nexus.agent.ai.retrieval.RetrievalQuery;
import com.nexus.agent.ai.retrieval.ScopeFilter;
import com.nexus.agent.ai.support.FakeCandidateRetriever;
import com.nexus.agent.ai.support.FakeEmbeddingClient;
import com.nexus.agent.ai.support.FakeLlmClient;
import com.nexus.agent.ai.support.FakeRecommendationComposer;
import com.nexus.agent.ai.support.InMemoryVectorStore;
import com.nexus.agent.ai.vector.CollectionSpec;
import com.nexus.agent.ai.vector.ScoredPoint;
import com.nexus.agent.ai.vector.VectorPoint;
import com.nexus.agent.ai.vector.filter.FilterCondition;
import com.nexus.agent.ai.vector.filter.PayloadFilter;
import com.nexus.agent.ai.llm.ChatMessage;
import com.nexus.agent.ai.llm.LlmRequest;
import com.nexus.agent.ai.llm.LlmResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueba que los contratos de AGENT-00 (§4) compilan y que los dobles en memoria se comportan: es la
 * superficie contra la que AGENT-02 se programa sin el motor real.
 */
class AgentContractTest {

    @Test
    @DisplayName("CatalogEntry.invalidReason flags the documented problems (R24)")
    void catalogEntryValidation() {
        assertThat(validEntry("a1").invalidReason()).isEmpty();
        assertThat(cityEntryWithoutCoords().invalidReason()).contains("CITY sin coordenadas");
        assertThat(validEntryBuilderCost(500, 200).invalidReason()).contains("costMin > costMax");
    }

    @Test
    @DisplayName("catalog PriceBand.of uses the per-person bands")
    void priceBandPerPerson() {
        assertThat(PriceBand.of(0)).isEqualTo(PriceBand.FREE);
        assertThat(PriceBand.of(200)).isEqualTo(PriceBand.LOW);
        assertThat(PriceBand.of(800)).isEqualTo(PriceBand.MID);
        assertThat(PriceBand.of(3000)).isEqualTo(PriceBand.HIGH);
        assertThat(PriceBand.of(3001)).isEqualTo(PriceBand.PREMIUM);
    }

    @Test
    @DisplayName("PayloadFilter and FilterCondition enforce their invariants")
    void filterInvariants() {
        assertThat(PayloadFilter.empty().isEmpty()).isTrue();
        assertThatThrownBy(() -> new FilterCondition.MatchAny("k", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FilterCondition.Range("k", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PreferenceSignal rejects invalid names and levels")
    void preferenceSignalValidation() {
        assertThat(new PreferenceSignal("Cine de autor", 80).level()).isEqualTo(80);
        assertThatThrownBy(() -> new PreferenceSignal("x", 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("InMemoryVectorStore filters, groups by activity and keeps the best score")
    void inMemorySearchGroupedWithFilter() {
        FakeEmbeddingClient emb = new FakeEmbeddingClient(16);
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.ensureCollection(new CollectionSpec("c", 16, List.of()));

        float[] q = emb.embed(List.of("picnic en el parque"), Duration.ofSeconds(1)).get(0);
        // dos puntos de la misma actividad (grupo) y uno de otra, con distinta categoría
        store.upsert(
                "c",
                List.of(
                        point("a1:p1", q, "a1", "AIRE_LIBRE"),
                        point("a1:p2", emb.embed(List.of("otro lugar"), Duration.ofSeconds(1)).get(0), "a1", "AIRE_LIBRE"),
                        point("a2:p1", q, "a2", "CULTURA_Y_ESPECTACULOS")));

        PayloadFilter filter =
                new PayloadFilter(List.of(new FilterCondition.Match("bankCategory", "AIRE_LIBRE")), List.of());
        List<ScoredPoint> hits = store.searchGrouped("c", q, filter, "activityId", 10);

        assertThat(hits).hasSize(1); // solo a1 pasa el filtro; un resultado por actividad
        assertThat(hits.get(0).id()).isEqualTo("a1:p1"); // el de mejor score del grupo
        assertThat(store.calls()).anyMatch(c -> c.startsWith("searchGrouped:"));
    }

    @Test
    @DisplayName("InMemoryVectorStore honours GeoRadius via Haversine and alias")
    void geoRadiusAndAlias() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.ensureCollection(new CollectionSpec("real", 2, List.of()));
        float[] v = {1f, 0f};
        store.upsert("real", List.of(
                new VectorPoint("near", v, Map.of("activityId", "n", "geo", Map.of("lat", 19.35, "lon", -99.16))),
                new VectorPoint("far", v, Map.of("activityId", "f", "geo", Map.of("lat", 25.0, "lon", -100.0)))));
        store.pointAlias("alias", "real");

        PayloadFilter near =
                new PayloadFilter(List.of(new FilterCondition.GeoRadius("geo", 19.35, -99.16, 5000)), List.of());
        List<ScoredPoint> hits = store.searchGrouped("alias", v, near, "activityId", 10);

        assertThat(hits).extracting(ScoredPoint::id).containsExactly("near");
    }

    @Test
    @DisplayName("FakeEmbeddingClient is deterministic and counts calls, and failNext fails once")
    void fakeEmbeddingBehaviour() {
        FakeEmbeddingClient emb = new FakeEmbeddingClient(8);
        float[] a = emb.embed(List.of("hola"), Duration.ofSeconds(1)).get(0);
        float[] b = emb.embed(List.of("hola"), Duration.ofSeconds(1)).get(0);
        assertThat(a).containsExactly(b);
        assertThat(emb.calls()).isEqualTo(2);
        assertThat(emb.textsEmbedded()).isEqualTo(2);

        emb.failNext(new IllegalStateException("boom"));
        assertThatThrownBy(() -> emb.embed(List.of("x"), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(emb.embed(List.of("y"), Duration.ofSeconds(1))).hasSize(1); // ya no falla
    }

    @Test
    @DisplayName("FakeLlmClient replays the script and records requests")
    void fakeLlmBehaviour() {
        FakeLlmClient llm = new FakeLlmClient(List.of("{\"picks\":[]}", new IllegalStateException("caído")));
        LlmRequest req = new LlmRequest(List.of(new ChatMessage(ChatMessage.Role.USER, "hola")), null, 320);

        LlmResponse first = llm.chat(req, Duration.ofSeconds(7));
        assertThat(first.content()).contains("picks");
        assertThat(llm.received()).hasSize(1);
        assertThatThrownBy(() -> llm.chat(req, Duration.ofSeconds(7)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the retriever/composer seam produces drafts that are a subset of the candidates")
    void seamProducesSubsetOfCandidates() {
        List<CandidateActivity> candidates = List.of(
                candidate("a1", "Picnic en el parque"),
                candidate("a2", "Ópera en Bellas Artes"),
                candidate("a3", "Café de especialidad"));
        FakeCandidateRetriever retriever = new FakeCandidateRetriever(candidates);
        FakeRecommendationComposer composer = new FakeRecommendationComposer(2);

        RetrievalQuery query = new RetrievalQuery(
                List.of(new PreferenceSignal("Aire libre", 90)),
                List.of(new PreferenceSignal("Café", 70)),
                BankCategory.AIRE_LIBRE,
                ScopeFilter.CITY,
                500,
                180,
                Set.of(),
                List.of());

        List<CandidateActivity> got = retriever.retrieve(query);
        Composition composition = composer.compose(query, got);

        assertThat(composition.drafts()).hasSize(2); // maxResults
        assertThat(composition.drafts())
                .allMatch(d -> got.stream().anyMatch(c -> c.activityId().equals(d.activityId())));
        assertThat(composition.trace().mode().isFallback()).isFalse();
        assertThat(retriever.lastQuery()).isSameAs(query);
    }

    // --- helpers ---------------------------------------------------------------------------

    private static VectorPoint point(String id, float[] v, String activityId, String category) {
        return new VectorPoint(id, v, Map.of("activityId", activityId, "bankCategory", category));
    }

    private static CandidateActivity candidate(String id, String title) {
        return new CandidateActivity(
                id, title, "desc", BankCategory.AIRE_LIBRE, 0, 200, 60, 120,
                LocationScope.CITY, id + ":p", "Lugar", "Coyoacán", 0.9);
    }

    private static CatalogEntry validEntry(String id) {
        return new CatalogEntry(id, "p1", "Picnic", "desc", "Parque", BankCategory.AIRE_LIBRE,
                WellnessDimension.AMBIENTAL, LocationScope.CITY, "Coyoacán", 19.35, -99.16,
                0, 200, true, Set.of(WeatherOk.SUNNY), 60, 120, 240);
    }

    private static CatalogEntry cityEntryWithoutCoords() {
        return new CatalogEntry("a2", "p2", "Museo", "desc", "Museo", BankCategory.CULTURA_Y_ESPECTACULOS,
                WellnessDimension.INTELECTUAL, LocationScope.CITY, "Cuauhtémoc", null, null,
                0, 100, false, Set.of(WeatherOk.ANY), 60, 90, 120);
    }

    private static CatalogEntry validEntryBuilderCost(int costMin, int costMax) {
        return new CatalogEntry("a3", "p3", "Café", "desc", "Café", BankCategory.COMER_Y_BEBER,
                WellnessDimension.SOCIAL, LocationScope.CITY, "Cuauhtémoc", 19.41, -99.16,
                costMin, costMax, false, Set.of(WeatherOk.ANY), 45, 60, 90);
    }
}
