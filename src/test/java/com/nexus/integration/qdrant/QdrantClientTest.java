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
                        3L,
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
