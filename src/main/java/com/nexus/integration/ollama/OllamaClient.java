package com.nexus.integration.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.config.AgentProperties;
import com.nexus.exception.AgentTimeoutException;
import com.nexus.exception.AgentUnavailableException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * [T-06] U3 - Cliente de la API HTTP de Ollama.
 *
 * <p>Cualquier falla (sin conexion, error HTTP, tiempo excedido o respuesta con otra forma) se
 * reporta como {@link AgentUnavailableException}; quien llama decide el respaldo.
 */
public class OllamaClient {

    /** Mantiene el modelo en memoria entre solicitudes: cargarlo en frio tarda minutos (R1). */
    private static final String KEEP_ALIVE = "30m";

    private final RestClient http;
    private final AgentProperties properties;

    public OllamaClient(RestClient.Builder builder, AgentProperties properties) {
        this.http = builder.baseUrl(properties.ollamaUrl().toString()).build();
        this.properties = properties;
    }

    /** Embedding de un texto con el modelo de embeddings configurado. */
    public float[] embed(String text) {
        JsonNode body = post("/api/embed", Map.of("model", properties.embedModel(), "input", text));
        return toVector(body.path("embeddings").path(0));
    }

    /**
     * [t06b-agente-java] Embeddings de varios textos en una sola llamada, en el mismo orden.
     *
     * @throws AgentUnavailableException si la respuesta no trae un vector por texto
     */
    public List<float[]> embed(List<String> texts) {
        JsonNode body = post("/api/embed", Map.of("model", properties.embedModel(), "input", texts));
        JsonNode vectors = body.path("embeddings");
        if (!vectors.isArray() || vectors.size() != texts.size()) {
            throw new AgentUnavailableException(
                    "Ollama devolvio " + vectors.size() + " embeddings para " + texts.size() + " textos");
        }
        List<float[]> result = new ArrayList<>(texts.size());
        for (JsonNode vector : vectors) {
            result.add(toVector(vector));
        }
        return result;
    }

    /**
     * [t06b-agente-java] Chat con el modelo de redaccion, con la respuesta forzada a un esquema JSON
     * ({@code format}); requiere Ollama 0.5 o mas nuevo.
     *
     * @param schema esquema JSON de la respuesta ({@code OutputSchema})
     * @param timeout tiempo maximo de esta llamada
     * @throws AgentTimeoutException si se pasa del tiempo
     * @throws AgentUnavailableException si Ollama falla o responde con otra forma
     */
    public ChatResult chat(
            List<ChatMessage> messages, Map<String, Object> schema, ChatOptions options, Duration timeout) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.chatModel());
        request.put("messages", messages);
        request.put("stream", false);
        request.put("format", schema);
        request.put("keep_alive", KEEP_ALIVE);
        request.put("options", optionsOf(options));
        long start = System.nanoTime();
        JsonNode body = await(CompletableFuture.supplyAsync(() -> post("/api/chat", request)), timeout);
        long wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        JsonNode content = body.path("message").path("content");
        if (!content.isTextual()) {
            throw new AgentUnavailableException("Ollama no devolvio el mensaje del chat");
        }
        return new ChatResult(
                content.asText(),
                body.path("done_reason").asText(null),
                body.path("prompt_eval_count").asInt(),
                body.path("eval_count").asInt(),
                wallMs);
    }

    private static Map<String, Object> optionsOf(ChatOptions options) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("temperature", options.temperature());
        map.put("num_ctx", options.numCtx());
        map.put("num_predict", options.numPredict());
        if (options.seed() != null) {
            map.put("seed", options.seed());
        }
        return map;
    }

    private static float[] toVector(JsonNode vector) {
        if (!vector.isArray() || vector.isEmpty()) {
            throw new AgentUnavailableException("Ollama no devolvio un embedding");
        }
        float[] result = new float[vector.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = (float) vector.get(i).asDouble();
        }
        return result;
    }

    private JsonNode post(String path, Map<String, Object> request) {
        try {
            JsonNode body =
                    http.post()
                            .uri(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(JsonNode.class);
            if (body == null) {
                throw new AgentUnavailableException("Ollama respondio sin cuerpo");
            }
            return body;
        } catch (RestClientException e) {
            throw new AgentUnavailableException("Ollama no esta disponible", e);
        }
    }

    private static JsonNode await(CompletableFuture<JsonNode> call, Duration timeout) {
        try {
            return call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new AgentTimeoutException("Ollama excedio " + timeout.toMillis() + " ms", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AgentUnavailableException unavailable) {
                throw unavailable;
            }
            throw new AgentUnavailableException("Ollama no esta disponible", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentUnavailableException("Se interrumpio la llamada a Ollama", e);
        }
    }
}
