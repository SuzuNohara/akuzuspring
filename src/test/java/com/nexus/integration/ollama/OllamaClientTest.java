package com.nexus.integration.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nexus.exception.AgentTimeoutException;
import com.nexus.exception.AgentUnavailableException;
import com.nexus.integration.AgentTestProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** [T-06] U3 - Cliente HTTP de Ollama: embeddings y generacion de texto. */
class OllamaClientTest {

    private static final ChatOptions OPTIONS = new ChatOptions(0.2, 8192, 600, null);

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
    @DisplayName("U10.1: should send the chat with its schema, options and keep alive and read the result")
    void shouldSendTheChatWithItsSchemaOptionsAndKeepAliveAndReadTheResult() {
        server.expect(requestTo("http://ollama.test/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"model\":\"mistral:7b\","
                                + "\"messages\":[{\"role\":\"system\",\"content\":\"reglas\"},"
                                + "{\"role\":\"user\",\"content\":\"candidatas\"}],"
                                + "\"stream\":false,\"format\":{\"type\":\"object\"},\"keep_alive\":\"30m\","
                                + "\"options\":{\"temperature\":0.2,\"num_ctx\":8192,\"num_predict\":600,\"seed\":7}}",
                        true))
                .andRespond(withSuccess(
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"picks\\\":[]}\"},"
                                + "\"done_reason\":\"stop\",\"prompt_eval_count\":812,\"eval_count\":95}",
                        MediaType.APPLICATION_JSON));

        ChatResult result = client.chat(
                List.of(new ChatMessage("system", "reglas"), new ChatMessage("user", "candidatas")),
                Map.of("type", "object"),
                new ChatOptions(0.2, 8192, 600, 7),
                Duration.ofSeconds(5));

        assertThat(result.content()).isEqualTo("{\"picks\":[]}");
        assertThat(result.doneReason()).isEqualTo("stop");
        assertThat(result.promptTokens()).isEqualTo(812);
        assertThat(result.evalTokens()).isEqualTo(95);
        assertThat(result.wallMs()).isNotNegative();
        server.verify();
    }

    @Test
    @DisplayName("U10.2: should fail with a timeout when the chat exceeds its time limit")
    void shouldFailWithATimeoutWhenTheChatExceedsItsTimeLimit() {
        server.expect(requestTo("http://ollama.test/api/chat"))
                .andRespond(request -> {
                    sleep(1_000);
                    return withSuccess("{}", MediaType.APPLICATION_JSON).createResponse(request);
                });

        assertThatThrownBy(() -> client.chat(List.of(), Map.of(), OPTIONS, Duration.ofMillis(100)))
                .isInstanceOf(AgentTimeoutException.class);
    }

    @Test
    @DisplayName("U10.3: should fail as unavailable, not as timeout, on a bad request")
    void shouldFailAsUnavailableNotAsTimeoutOnABadRequest() {
        server.expect(requestTo("http://ollama.test/api/chat")).andRespond(withBadRequest());

        assertThatThrownBy(() -> client.chat(List.of(), Map.of(), OPTIONS, Duration.ofSeconds(5)))
                .isInstanceOf(AgentUnavailableException.class)
                .isNotInstanceOf(AgentTimeoutException.class);
    }

    @Test
    @DisplayName("U10.3: should fail as unavailable, not as timeout, on a server error")
    void shouldFailAsUnavailableNotAsTimeoutOnAServerError() {
        server.expect(requestTo("http://ollama.test/api/chat")).andRespond(withServerError());

        assertThatThrownBy(() -> client.chat(List.of(), Map.of(), OPTIONS, Duration.ofSeconds(5)))
                .isInstanceOf(AgentUnavailableException.class)
                .isNotInstanceOf(AgentTimeoutException.class);
    }

    @Test
    @DisplayName("U10.3: should fail as unavailable, not as timeout, when the connection is refused")
    void shouldFailAsUnavailableNotAsTimeoutWhenTheConnectionIsRefused() {
        server.expect(requestTo("http://ollama.test/api/chat"))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        assertThatThrownBy(() -> client.chat(List.of(), Map.of(), OPTIONS, Duration.ofSeconds(5)))
                .isInstanceOf(AgentUnavailableException.class)
                .isNotInstanceOf(AgentTimeoutException.class);
    }

    @Test
    @DisplayName("U10.4: should embed a batch and return one vector per text in order")
    void shouldEmbedABatchAndReturnOneVectorPerTextInOrder() {
        server.expect(requestTo("http://ollama.test/api/embed"))
                .andExpect(content().json("{\"model\":\"nomic-embed-text\",\"input\":[\"uno\",\"dos\"]}", true))
                .andRespond(withSuccess("{\"embeddings\":[[1.0,0.0],[0.0,1.0]]}", MediaType.APPLICATION_JSON));

        List<float[]> vectors = client.embed(List.of("uno", "dos"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1.0f, 0.0f);
        assertThat(vectors.get(1)).containsExactly(0.0f, 1.0f);
    }

    @Test
    @DisplayName("U10.4: should fail as unavailable when the number of vectors does not match")
    void shouldFailAsUnavailableWhenTheNumberOfVectorsDoesNotMatch() {
        server.expect(requestTo("http://ollama.test/api/embed"))
                .andRespond(withSuccess("{\"embeddings\":[[1.0,0.0]]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed(List.of("uno", "dos")))
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
