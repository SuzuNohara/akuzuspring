package com.nexus.integration.qdrant;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.config.AgentProperties;
import com.nexus.exception.AgentUnavailableException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * [T-06] U4 - Cliente de la API REST de Qdrant (puerto 6333).
 *
 * <p>Cualquier falla se reporta como {@link AgentUnavailableException}; la seleccion de candidatas
 * cae entonces a MySQL (R8, R16), asi que una caida de Qdrant nunca deja la pantalla vacia.
 */
public class QdrantClient {

    private final RestClient http;

    public QdrantClient(RestClient.Builder builder, AgentProperties properties) {
        this.http = builder.baseUrl(properties.qdrantUrl().toString()).build();
    }

    /** Crea la coleccion con distancia coseno si todavia no existe. */
    public void ensureCollection(String name, int dimensions) {
        if (exists(name)) {
            return;
        }
        call(() -> http.put()
                .uri("/collections/{name}", name)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("vectors", Map.of("size", dimensions, "distance", "Cosine")))
                .retrieve()
                .toBodilessEntity());
    }

    /** Inserta o reemplaza puntos y espera a que queden indexados. */
    public void upsert(String name, List<QdrantPoint> points) {
        List<Map<String, Object>> body = new ArrayList<>();
        for (QdrantPoint point : points) {
            body.add(Map.of("id", point.id(), "vector", point.vector(), "payload", point.payload()));
        }
        call(() -> http.put()
                .uri("/collections/{name}/points?wait=true", name)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("points", body))
                .retrieve()
                .toBodilessEntity());
    }

    /** Numero exacto de puntos; 0 si la coleccion todavia no existe. */
    public long count(String name) {
        try {
            JsonNode body =
                    http.post()
                            .uri("/collections/{name}/points/count", name)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("exact", true))
                            .retrieve()
                            .body(JsonNode.class);
            return requireResult(body).path("count").asLong();
        } catch (HttpClientErrorException.NotFound e) {
            return 0;
        } catch (RestClientException e) {
            throw new AgentUnavailableException("Qdrant no esta disponible", e);
        }
    }

    /** Los {@code limit} puntos mas parecidos al vector que cumplen el filtro, del mas al menos parecido. */
    public List<ScoredId> search(String name, float[] vector, QdrantFilter filter, int limit) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("vector", vector);
        request.put("limit", limit);
        request.put("with_payload", false);
        Map<String, Object> conditions = filter.toJson();
        if (!conditions.isEmpty()) {
            request.put("filter", conditions);
        }
        JsonNode body =
                call(() -> http.post()
                        .uri("/collections/{name}/points/search", name)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(JsonNode.class));
        List<ScoredId> hits = new ArrayList<>();
        for (JsonNode hit : requireResult(body)) {
            hits.add(new ScoredId(hit.path("id").asLong(), hit.path("score").asDouble()));
        }
        return hits;
    }

    private boolean exists(String name) {
        try {
            http.get().uri("/collections/{name}", name).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw new AgentUnavailableException("Qdrant no esta disponible", e);
        } catch (RestClientException e) {
            throw new AgentUnavailableException("Qdrant no esta disponible", e);
        }
    }

    private static JsonNode requireResult(JsonNode body) {
        if (body == null || body.path("result").isMissingNode()) {
            throw new AgentUnavailableException("Qdrant respondio sin resultado");
        }
        return body.path("result");
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            throw new AgentUnavailableException("Qdrant no esta disponible", e);
        }
    }
}
