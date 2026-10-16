package com.nexus.integration.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nexus.exception.AgentUnavailableException;
import com.nexus.integration.AgentTestProperties;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** [T-06] U3 - Cliente HTTP de Ollama: embeddings y generacion de texto. */
class OllamaClientTest {

    private MockRestServiceServer server;
    private OllamaClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OllamaClient(builder, AgentTestProperties.defaults());
    }

    @Test
    @DisplayName("O1: should send the embedding model and text and return the vector")
    void shouldSendTheEmbeddingModelAndTextAndReturnTheVector() {
        server.expect(requestTo("http://ollama.test/api/embed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"model\":\"nomic-embed-text\",\"input\":\"search_query: cena\"}"))
                .andRespond(withSuccess("{\"embeddings\":[[0.5,-0.25,1.0]]}", MediaType.APPLICATION_JSON));

        float[] vector = client.embed("search_query: cena");

        assertThat(vector).containsExactly(0.5f, -0.25f, 1.0f);
        server.verify();
    }

    @Test
    @DisplayName("O2: should request non streamed JSON from the chat model")
    void shouldRequestNonStreamedJsonFromTheChatModel() {
        server.expect(requestTo("http://ollama.test/api/generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"model\":\"mistral:7b\",\"prompt\":\"hola\",\"stream\":false,\"format\":\"json\","
                                + "\"keep_alive\":\"30m\",\"options\":{\"temperature\":0.3,\"num_predict\":300}}"))
                .andRespond(withSuccess("{\"response\":\"{\\\"items\\\":[]}\",\"done\":true}", MediaType.APPLICATION_JSON));

        String json = client.generateJson("hola", Duration.ofSeconds(5));

        assertThat(json).isEqualTo("{\"items\":[]}");
        server.verify();
    }

    @Test
    @DisplayName("O3: should fail as unavailable when the call exceeds its time limit")
    void shouldFailAsUnavailableWhenTheCallExceedsItsTimeLimit() {
        server.expect(requestTo("http://ollama.test/api/generate"))
                .andRespond(request -> {
                    sleep(1_000);
                    return withSuccess("{\"response\":\"{}\"}", MediaType.APPLICATION_JSON).createResponse(request);
                });

        assertThatThrownBy(() -> client.generateJson("hola", Duration.ofMillis(100)))
                .isInstanceOf(AgentUnavailableException.class);
    }

    @Test
    @DisplayName("O4: should fail as unavailable on a server error")
    void shouldFailAsUnavailableOnAServerError() {
        server.expect(requestTo("http://ollama.test/api/embed")).andRespond(withServerError());

        assertThatThrownBy(() -> client.embed("x")).isInstanceOf(AgentUnavailableException.class);
    }

    @Test
    @DisplayName("O4: should fail as unavailable when the connection is refused")
    void shouldFailAsUnavailableWhenTheConnectionIsRefused() {
        server.expect(requestTo("http://ollama.test/api/embed"))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        assertThatThrownBy(() -> client.embed("x")).isInstanceOf(AgentUnavailableException.class);
    }

    @Test
    @DisplayName("O5: should fail as unavailable when the response has an unexpected shape")
    void shouldFailAsUnavailableWhenTheResponseHasAnUnexpectedShape() {
        server.expect(requestTo("http://ollama.test/api/embed"))
                .andRespond(withSuccess("{\"model\":\"nomic-embed-text\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed("x")).isInstanceOf(AgentUnavailableException.class);
    }

    @Test
    @DisplayName("O5: should fail as unavailable when generate returns no response text")
    void shouldFailAsUnavailableWhenGenerateReturnsNoResponseText() {
        server.expect(requestTo("http://ollama.test/api/generate"))
                .andRespond(withSuccess("{\"done\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateJson("hola", Duration.ofSeconds(5)))
                .isInstanceOf(AgentUnavailableException.class);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
