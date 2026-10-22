package com.nexus.integration.qdrant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nexus.exception.AgentUnavailableException;
import com.nexus.integration.AgentTestProperties;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** [T-06] U4 - Cliente REST de Qdrant: coleccion, carga, conteo y busqueda. */
class QdrantClientTest {

    private static final String OK = "{\"result\":true,\"status\":\"ok\"}";

    private MockRestServiceServer server;
    private QdrantClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new QdrantClient(builder, AgentTestProperties.defaults());
    }

    @Test
    @DisplayName("Q1: should not recreate a collection that already exists")
    void shouldNotRecreateACollectionThatAlreadyExists() {
        server.expect(requestTo("http://qdrant.test/collections/activities"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"result\":{\"status\":\"green\"}}", MediaType.APPLICATION_JSON));
        server.expect(never(), requestTo("http://qdrant.test/collections/activities"))
                .andExpect(method(HttpMethod.PUT));

        client.ensureCollection("activities", 768);

        server.verify();
    }

    @Test
    @DisplayName("Q1: should create a cosine collection when it does not exist")
    void shouldCreateACosineCollectionWhenItDoesNotExist() {
        server.expect(requestTo("http://qdrant.test/collections/activities"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());
        server.expect(requestTo("http://qdrant.test/collections/activities"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"vectors\":{\"size\":768,\"distance\":\"Cosine\"}}"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        client.ensureCollection("activities", 768);

        server.verify();
    }

    @Test
    @DisplayName("Q2: should upsert points with their ids and payload")
    void shouldUpsertPointsWithTheirIdsAndPayload() {
        server.expect(requestTo("http://qdrant.test/collections/activities/points?wait=true"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json(
                        "{\"points\":[{\"id\":3,\"vector\":[0.5,1.0],"
                                + "\"payload\":{\"location_scope\":\"HOME\",\"dayparts\":[\"NIGHT\"]}}]}"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        client.upsert(
                "activities",
                List.of(new QdrantPoint(
                        "3",
                        new float[] {0.5f, 1.0f},
                        Map.of("location_scope", "HOME", "dayparts", List.of("NIGHT")))));

        server.verify();
    }

    @Test
    @DisplayName("Q4: should read the exact number of points")
    void shouldReadTheExactNumberOfPoints() {
        server.expect(requestTo("http://qdrant.test/collections/activities/points/count"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"exact\":true}"))
                .andRespond(withSuccess("{\"result\":{\"count\":80}}", MediaType.APPLICATION_JSON));

        assertThat(client.count("activities")).isEqualTo(80);
    }

    @Test
    @DisplayName("Q4: should count zero when the collection does not exist yet")
    void shouldCountZeroWhenTheCollectionDoesNotExistYet() {
        server.expect(requestTo("http://qdrant.test/collections/activities/points/count"))
                .andRespond(withResourceNotFound());

        assertThat(client.count("activities")).isZero();
    }

    @Test
    @DisplayName("Q3: should search with the vector, the filter and the limit")
    void shouldSearchWithTheVectorTheFilterAndTheLimit() {
        server.expect(requestTo("http://qdrant.test/collections/activities/points/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"vector\":[1.0,0.0],\"limit\":40,\"with_payload\":false,"
                                + "\"filter\":{\"must\":[{\"key\":\"location_scope\",\"match\":{\"any\":[\"HOME\"]}}]}}"))
                .andRespond(withSuccess(
                        "{\"result\":[{\"id\":12,\"score\":0.91},{\"id\":4,\"score\":0.88}]}",
                        MediaType.APPLICATION_JSON));

        List<ScoredId> hits =
                client.search(
                        "activities",
                        new float[] {1.0f, 0.0f},
                        new QdrantFilter(Set.of("HOME"), null, null, null, null, null),
                        40);

        assertThat(hits).containsExactly(new ScoredId(12L, 0.91), new ScoredId(4L, 0.88));
    }

    private static final QdrantFilter NONE = new QdrantFilter(null, null, null, null, null, null);
    private static final String UUID_A = "dcc3bc0e-d70f-5ce6-8a6d-57a3aeb1691f";
    private static final String UUID_B = "ebaca0c4-7913-5968-a3b1-73d37cc537dd";

    @Test
    @DisplayName("U3.5: should send UUID ids as text when upserting offers")
    void shouldSendUuidIdsAsTextWhenUpsertingOffers() {
        server.expect(requestTo("http://qdrant.test/collections/catalog/points?wait=true"))
                .andExpect(content().json(
                        "{\"points\":[{\"id\":\"" + UUID_A + "\",\"vector\":[1.0],\"payload\":{\"activity_id\":12}}]}",
                        true))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        client.upsert("catalog", List.of(new QdrantPoint(UUID_A, new float[] {1.0f}, Map.of("activity_id", 12))));

        server.verify();
    }

    @Test
    @DisplayName("U3.5: should search by groups and return the best hit of each with its payload by score")
    void shouldSearchByGroupsAndReturnTheBestHitOfEachWithItsPayloadByScore() {
        server.expect(requestTo("http://qdrant.test/collections/nexus_catalog_active/points/query/groups"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"query\":[1.0,0.0],\"group_by\":\"activity_id\",\"group_size\":1,\"limit\":30,"
                                + "\"with_payload\":true,"
                                + "\"filter\":{\"must\":[{\"key\":\"location_scope\",\"match\":{\"any\":[\"HOME\"]}}]}}",
                        true))
                .andRespond(withSuccess(
                        "{\"result\":{\"groups\":["
                                + "{\"id\":4,\"hits\":[{\"id\":\"" + UUID_B + "\",\"score\":0.71,"
                                + "\"payload\":{\"activity_id\":4,\"place_id\":40}}]},"
                                + "{\"id\":12,\"hits\":[{\"id\":\"" + UUID_A + "\",\"score\":0.93,"
                                + "\"payload\":{\"activity_id\":12}}]},"
                                + "{\"id\":9,\"hits\":[]}]}}",
                        MediaType.APPLICATION_JSON));

        List<QdrantHit> hits = client.searchGroups(
                "nexus_catalog_active",
                new float[] {1.0f, 0.0f},
                new QdrantFilter(Set.of("HOME"), null, null, null, null, null),
                "activity_id",
                30);

        assertThat(hits).extracting(QdrantHit::id).containsExactly(UUID_A, UUID_B);
        assertThat(hits.get(0).score()).isEqualTo(0.93);
        assertThat(hits.get(1).payload()).containsEntry("activity_id", 4).containsEntry("place_id", 40);
    }

    @Test
    @DisplayName("U3.6: should count exactly with the filter")
    void shouldCountExactlyWithTheFilter() {
        server.expect(requestTo("http://qdrant.test/collections/catalog/points/count"))
                .andExpect(content().json(
                        "{\"exact\":true,\"filter\":{\"must\":[{\"key\":\"location_scope\",\"match\":{\"any\":[\"CITY\"]}}]}}",
                        true))
                .andRespond(withSuccess("{\"result\":{\"count\":17}}", MediaType.APPLICATION_JSON));

        long count = client.count("catalog", new QdrantFilter(Set.of("CITY"), null, null, null, null, null));

        assertThat(count).isEqualTo(17);
    }

    @Test
    @DisplayName("U3.6: should create one payload index per field")
    void shouldCreateOnePayloadIndexPerField() {
        server.expect(requestTo("http://qdrant.test/collections/catalog/index?wait=true"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"field_name\":\"location\",\"field_schema\":\"geo\"}", true))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://qdrant.test/collections/catalog/index?wait=true"))
                .andExpect(content().json("{\"field_name\":\"activity_id\",\"field_schema\":\"integer\"}", true))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("location", "geo");
        schema.put("activity_id", "integer");
        client.ensurePayloadIndexes("catalog", schema);

        server.verify();
    }

    @Test
    @DisplayName("U3.7: should point the alias with a single atomic delete and create")
    void shouldPointTheAliasWithASingleAtomicDeleteAndCreate() {
        server.expect(requestTo("http://qdrant.test/collections/aliases"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"actions\":[{\"delete_alias\":{\"alias_name\":\"nexus_catalog_active\"}},"
                                + "{\"create_alias\":{\"collection_name\":\"nexus_catalog__bge-m3__d1024__t2\","
                                + "\"alias_name\":\"nexus_catalog_active\"}}]}",
                        true))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        client.pointAlias("nexus_catalog_active", "nexus_catalog__bge-m3__d1024__t2");

        server.verify();
    }

    @Test
    @DisplayName("U3.7: should page through scroll until next_page_offset is null")
    void shouldPageThroughScrollUntilNextPageOffsetIsNull() {
        server.expect(requestTo("http://qdrant.test/collections/catalog/points/scroll"))
                .andExpect(content().json(
                        "{\"limit\":256,\"with_payload\":[\"text_hash\"],\"with_vector\":false}", true))
                .andRespond(withSuccess(
                        "{\"result\":{\"points\":[{\"id\":\"" + UUID_A + "\",\"payload\":{\"text_hash\":\"aa\"}}],"
                                + "\"next_page_offset\":\"" + UUID_B + "\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://qdrant.test/collections/catalog/points/scroll"))
                .andExpect(content().json(
                        "{\"limit\":256,\"with_payload\":[\"text_hash\"],\"with_vector\":false,"
                                + "\"offset\":\"" + UUID_B + "\"}",
                        true))
                .andRespond(withSuccess(
                        "{\"result\":{\"points\":[{\"id\":\"" + UUID_B + "\",\"payload\":{\"text_hash\":\"bb\"}}],"
                                + "\"next_page_offset\":null}}",
                        MediaType.APPLICATION_JSON));

        Map<String, String> hashes = client.payloadValues("catalog", "text_hash");

        assertThat(hashes).containsExactly(Map.entry(UUID_A, "aa"), Map.entry(UUID_B, "bb"));
        server.verify();
    }

    @Test
    @DisplayName("U3.7: should delete the given ids")
    void shouldDeleteTheGivenIds() {
        server.expect(requestTo("http://qdrant.test/collections/catalog/points/delete?wait=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"points\":[\"" + UUID_A + "\"]}", true))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        client.delete("catalog", List.of(UUID_A));

        server.verify();
    }

    @Test
    @DisplayName("U3.8: should fail as unavailable when a grouped search gets a server error")
    void shouldFailAsUnavailableWhenAGroupedSearchGetsAServerError() {
        server.expect(requestTo("http://qdrant.test/collections/catalog/points/query/groups"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.searchGroups("catalog", new float[] {1f}, NONE, "activity_id", 30))
                .isInstanceOf(AgentUnavailableException.class);
    }

    @Test
    @DisplayName("U3.8: should fail as unavailable when the alias change times out")
    void shouldFailAsUnavailableWhenTheAliasChangeTimesOut() {
        server.expect(requestTo("http://qdrant.test/collections/aliases"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatThrownBy(() -> client.pointAlias("a", "c")).isInstanceOf(AgentUnavailableException.class);
    }

    @Test
    @DisplayName("Q5: should fail as unavailable when Qdrant answers with an error")
    void shouldFailAsUnavailableWhenQdrantAnswersWithAnError() {
        server.expect(requestTo("http://qdrant.test/collections/activities/points/search"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.search(
                        "activities", new float[] {1f}, new QdrantFilter(null, null, null, null, null, null), 5))
                .isInstanceOf(AgentUnavailableException.class);
    }

    @Test
    @DisplayName("Q5: should fail as unavailable when Qdrant is down")
    void shouldFailAsUnavailableWhenQdrantIsDown() {
        server.expect(requestTo("http://qdrant.test/collections/activities/points/count"))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        assertThatThrownBy(() -> client.count("activities")).isInstanceOf(AgentUnavailableException.class);
    }
}
