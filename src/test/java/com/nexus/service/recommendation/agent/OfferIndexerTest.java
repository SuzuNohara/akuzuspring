package com.nexus.service.recommendation.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexus.integration.AgentTestProperties;
import com.nexus.integration.ollama.OllamaClient;
import com.nexus.integration.qdrant.QdrantClient;
import com.nexus.integration.qdrant.QdrantPoint;
import com.nexus.model.IdeaCategory;
import com.nexus.service.recommendation.agent.OfferCatalog.CatalogLoad;
import com.nexus.service.recommendation.agent.OfferIndexer.IndexReport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/** [t06b-agente-java] U4 - Indexador de ofertas: idempotente por {@code text_hash}. */
class OfferIndexerTest {

    /** Coleccion que sale de {@code nomic-embed-text} en las propiedades de prueba. */
    private static final String COLLECTION = "nexus_catalog__nomic-embed-text__d1024__t2";

    private final OfferCatalog catalog = mock(OfferCatalog.class);
    private final OllamaClient ollama = mock(OllamaClient.class);
    private final QdrantClient qdrant = mock(QdrantClient.class);
    private final OfferIndexer indexer = new OfferIndexer(catalog, ollama, qdrant, AgentTestProperties.defaults());

    @BeforeEach
    void embedsReturnOneVectorPerText() {
        when(ollama.embed(anyList())).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            return texts.stream().map(t -> new float[OfferIndexer.DIMENSION]).toList();
        });
    }

    static Offer.OfferBuilder home(long id) {
        return Offer.builder()
                .activityId(id)
                .title("Actividad " + id)
                .description("Descripcion")
                .rationale("Razon")
                .activityType("BOARD_GAME")
                .dimension(IdeaCategory.SOCIAL)
                .interests(List.of())
                .ambience(List.of("QUIET"))
                .dayparts(List.of("NIGHT"))
                .locationScope("HOME")
                .priceBand("FREE")
                .costMxnPp(0)
                .outdoor(false)
                .weatherOk(List.of("ANY"))
                .durationMin(60)
                .durationAvg(90)
                .durationMax(120);
    }

    static Offer city(long id, long placeId) {
        return home(id).locationScope("CITY").placeId(placeId).placeName("Lugar").borough("Coyoacán")
                .lat(19.35).lon(-99.16).priceBand("LOW").costMxnPp(150).outdoor(true)
                .weatherOk(List.of("SUNNY", "CLOUDY")).build();
    }

    private void catalogHas(Offer... offers) {
        when(catalog.load()).thenReturn(new CatalogLoad(List.of(offers), List.of()));
    }

    private static Map<String, String> hashesOf(Offer... offers) {
        Map<String, String> hashes = new HashMap<>();
        for (Offer o : offers) {
            hashes.put(CanonicalText.pointId(o), CanonicalText.hash("nomic-embed-text", "t2", CanonicalText.of(o)));
        }
        return hashes;
    }

    @SuppressWarnings("unchecked")
    private List<QdrantPoint> upserted() {
        ArgumentCaptor<List<QdrantPoint>> points = ArgumentCaptor.forClass(List.class);
        verify(qdrant).upsert(eq(COLLECTION), points.capture());
        return points.getValue();
    }

    @Test
    @DisplayName("U4.1: should create the collection, its indexes and the points, then move the alias")
    void shouldCreateTheCollectionItsIndexesAndThePointsThenMoveTheAlias() {
        catalogHas(home(1).build(), home(2).build(), city(3, 30));
        when(qdrant.payloadValues(COLLECTION, "text_hash")).thenReturn(Map.of());

        IndexReport report = indexer.ensureIndexed();

        InOrder order = inOrder(qdrant);
        order.verify(qdrant).ensureCollection(COLLECTION, OfferIndexer.DIMENSION);
        order.verify(qdrant).ensurePayloadIndexes(eq(COLLECTION), anyMap());
        order.verify(qdrant).upsert(eq(COLLECTION), anyList());
        order.verify(qdrant).pointAlias("nexus_catalog_active", COLLECTION);
        assertThat(upserted()).hasSize(3);
        assertThat(report.collection()).isEqualTo(COLLECTION);
        assertThat(report.indexed()).isEqualTo(3);
        assertThat(report.skippedUnchanged()).isZero();
        assertThat(report.deleted()).isZero();
        verify(qdrant, never()).delete(any(), any());
    }

    @Test
    @DisplayName("U4.2: should embed nothing on a second run without changes and still point the alias")
    void shouldEmbedNothingOnASecondRunWithoutChanges() {
        Offer[] offers = {home(1).build(), home(2).build(), city(3, 30)};
        catalogHas(offers);
        when(qdrant.payloadValues(COLLECTION, "text_hash")).thenReturn(hashesOf(offers));

        IndexReport report = indexer.ensureIndexed();

        verify(ollama, never()).embed(anyList());
        verify(qdrant, never()).upsert(any(), any());
        verify(qdrant).pointAlias("nexus_catalog_active", COLLECTION);
        assertThat(report.indexed()).isZero();
        assertThat(report.skippedUnchanged()).isEqualTo(3);
    }

    @Test
    @DisplayName("U4.3: should re-embed only the offer that changed")
    void shouldReEmbedOnlyTheOfferThatChanged() {
        Offer before = home(2).build();
        Offer after = home(2).title("Titulo nuevo").build();
        catalogHas(home(1).build(), after);
        when(qdrant.payloadValues(COLLECTION, "text_hash")).thenReturn(hashesOf(home(1).build(), before));

        IndexReport report = indexer.ensureIndexed();

        assertThat(upserted()).extracting(QdrantPoint::id).containsExactly(CanonicalText.pointId(after));
        assertThat(report.indexed()).isEqualTo(1);
        assertThat(report.skippedUnchanged()).isEqualTo(1);
    }

    @Test
    @DisplayName("U4.4: should delete the point of an offer removed from MySQL")
    void shouldDeleteThePointOfAnOfferRemovedFromMysql() {
        Offer gone = home(9).build();
        catalogHas(home(1).build());
        when(qdrant.payloadValues(COLLECTION, "text_hash")).thenReturn(hashesOf(home(1).build(), gone));

        IndexReport report = indexer.ensureIndexed();

        verify(qdrant).delete(COLLECTION, List.of(CanonicalText.pointId(gone)));
        assertThat(report.deleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("U4.5: should embed in batches of 32")
    void shouldEmbedInBatchesOf32() {
        catalogHas(IntStream.rangeClosed(1, 70).mapToObj(i -> home(i).build()).toArray(Offer[]::new));
        when(qdrant.payloadValues(COLLECTION, "text_hash")).thenReturn(Map.of());

        IndexReport report = indexer.ensureIndexed();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> batches = ArgumentCaptor.forClass(List.class);
        verify(ollama, times(3)).embed(batches.capture());
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(32, 32, 6);
        verify(qdrant, times(3)).upsert(eq(COLLECTION), anyList());
        assertThat(report.indexed()).isEqualTo(70);
    }

    @Test
    @DisplayName("U4.6: should store location only for the city and dayparts and weather_ok as lists")
    void shouldStoreLocationOnlyForTheCityAndListsAsLists() {
        catalogHas(home(1).build(), city(3, 30));
        when(qdrant.payloadValues(COLLECTION, "text_hash")).thenReturn(Map.of());

        indexer.ensureIndexed();

        List<QdrantPoint> points = new ArrayList<>(upserted());
        Map<String, Object> atHome = points.get(0).payload();
        Map<String, Object> inCity = points.get(1).payload();
        assertThat(atHome).doesNotContainKeys("location", "place_id", "place_name", "borough");
        assertThat(atHome).containsEntry("activity_id", 1L).containsEntry("location_scope", "HOME")
                .containsEntry("dimension", "SOCIAL").containsEntry("dayparts", List.of("NIGHT"))
                .containsEntry("weather_ok", List.of("ANY")).containsEntry("is_outdoor", false)
                .containsKey("text_hash");
        assertThat(inCity).containsEntry("location", Map.of("lat", 19.35, "lon", -99.16))
                .containsEntry("place_id", 30L).containsEntry("place_name", "Lugar")
                .containsEntry("borough", "Coyoacán").containsEntry("cost_mxn_pp", 150)
                .containsEntry("weather_ok", List.of("SUNNY", "CLOUDY")).containsEntry("is_outdoor", true);
        assertThat(points.get(1).id()).isEqualTo(CanonicalText.pointId(city(3, 30)));
    }
}
