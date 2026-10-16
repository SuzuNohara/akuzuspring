package com.nexus.integration.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.config.AgentProperties;
import com.nexus.exception.AgentUnavailableException;
import java.time.Duration;
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

    private static final double TEMPERATURE = 0.3;
    private static final int MAX_TOKENS = 300;

    private final RestClient http;
    private final AgentProperties properties;

    public OllamaClient(RestClient.Builder builder, AgentProperties properties) {
        this.http = builder.baseUrl(properties.ollamaUrl().toString()).build();
        this.properties = properties;
    }

    /** Embedding de un texto con el modelo de embeddings configurado. */
    public float[] embed(String text) {
        JsonNode body = post("/api/embed", Map.of("model", properties.embedModel(), "input", text));
        JsonNode vector = body.path("embeddings").path(0);
        if (!vector.isArray() || vector.isEmpty()) {
            throw new AgentUnavailableException("Ollama no devolvio un embedding");
        }
        float[] result = new float[vector.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = (float) vector.get(i).asDouble();
        }
        return result;
    }

    /**
     * Genera texto en formato JSON con el modelo de redaccion.
     *
     * @param timeout tiempo maximo de esta llamada
     * @return el texto JSON que produjo el modelo, sin validar
     */
    public String generateJson(String prompt, Duration timeout) {
        Map<String, Object> request =
                Map.of(
                        "model", properties.chatModel(),
                        "prompt", prompt,
                        "stream", false,
                        "format", "json",
                        "keep_alive", KEEP_ALIVE,
                        "options", Map.of("temperature", TEMPERATURE, "num_predict", MAX_TOKENS));
        CompletableFuture<JsonNode> call = CompletableFuture.supplyAsync(() -> post("/api/generate", request));
        JsonNode body = await(call, timeout);
        JsonNode response = body.path("response");
        if (!response.isTextual()) {
            throw new AgentUnavailableException("Ollama no devolvio texto");
        }
        return response.asText();
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
            throw new AgentUnavailableException("Ollama excedio " + timeout.toMillis() + " ms", e);
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
