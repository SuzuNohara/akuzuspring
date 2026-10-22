package com.nexus.integration.qdrant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.config.AgentProperties;
import com.nexus.exception.AgentUnavailableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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

    /** Puntos por pagina al recorrer la coleccion con {@code scroll}. */
    static final int SCROLL_PAGE = 256;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {};

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

    /** [t06b-agente-java] Numero exacto de puntos que cumplen el filtro; 0 si la coleccion no existe. */
    public long count(String name, QdrantFilter filter) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("exact", true);
        putFilter(request, filter);
        try {
            JsonNode body =
                    http.post()
                            .uri("/collections/{name}/points/count", name)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(JsonNode.class);
            return requireResult(body).path("count").asLong();
        } catch (HttpClientErrorException.NotFound e) {
            return 0;
        } catch (RestClientException e) {
            throw new AgentUnavailableException("Qdrant no esta disponible", e);
        }
    }

    /**
     * [t06b-agente-java] Crea los indices de payload que usan los filtros (p. ej. {@code location →
     * geo}, {@code activity_id → integer}). Qdrant ignora los que ya existen.
     */
    public void ensurePayloadIndexes(String name, Map<String, String> schemaByField) {
        schemaByField.forEach((field, schema) -> call(() -> http.put()
                .uri("/collections/{name}/index?wait=true", name)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("field_name", field, "field_schema", schema))
                .retrieve()
                .toBodilessEntity()));
    }

    /**
     * [t06b-agente-java] Valor de un campo del payload de cada punto (id → valor), recorriendo la
     * coleccion por paginas sin traer vectores. Sirve para saber que ofertas cambiaron ({@code text_hash}).
     *
     * @implNote O(p) puntos en O(p / 256) llamadas.
     */
    public Map<String, String> payloadValues(String name, String key) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("limit", SCROLL_PAGE);
        request.put("with_payload", List.of(key));
        request.put("with_vector", false);
        while (true) {
            JsonNode result = requireResult(postJson("/collections/{name}/points/scroll", name, request));
            for (JsonNode point : result.path("points")) {
                values.put(point.path("id").asText(), point.path("payload").path(key).asText(null));
            }
            JsonNode next = result.path("next_page_offset");
            if (next.isMissingNode() || next.isNull()) {
                return values;
            }
            request.put("offset", next);
        }
    }

    /** [t06b-agente-java] Borra puntos por id y espera a que se apliquen. */
    public void delete(String name, Collection<String> ids) {
        postJson("/collections/{name}/points/delete?wait=true", name, Map.of("points", List.copyOf(ids)));
    }

    /** [t06b-agente-java] Apunta el alias a la coleccion en un solo cambio atomico (borrar y crear). */
    public void pointAlias(String alias, String collection) {
        List<Map<String, Object>> actions = List.of(
                Map.of("delete_alias", Map.of("alias_name", alias)),
                Map.of("create_alias", Map.of("collection_name", collection, "alias_name", alias)));
        call(() -> http.post()
                .uri("/collections/aliases")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("actions", actions))
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * [t06b-agente-java] El mejor punto de cada grupo ({@code groupBy}, p. ej. {@code activity_id}),
     * con su payload, del mas al menos parecido.
     *
     * @implNote O(limit log limit).
     */
    public List<QdrantHit> searchGroups(String name, float[] vector, QdrantFilter filter, String groupBy, int limit) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", vector);
        request.put("group_by", groupBy);
        request.put("group_size", 1);
        request.put("limit", limit);
        request.put("with_payload", true);
        putFilter(request, filter);
        JsonNode result = requireResult(postJson("/collections/{name}/points/query/groups", name, request));
        List<QdrantHit> hits = new ArrayList<>();
        for (JsonNode group : result.path("groups")) {
            JsonNode hit = group.path("hits").path(0);
            if (!hit.isMissingNode()) {
                hits.add(new QdrantHit(
                        hit.path("id").asText(),
                        hit.path("score").asDouble(),
                        MAPPER.convertValue(hit.path("payload"), PAYLOAD)));
            }
        }
        hits.sort(Comparator.comparingDouble(QdrantHit::score).reversed());
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

    private JsonNode postJson(String uri, String name, Object request) {
        return call(() -> http.post()
                .uri(uri, name)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class));
    }

    private static void putFilter(Map<String, Object> request, QdrantFilter filter) {
        Map<String, Object> conditions = filter == null ? Map.of() : filter.toJson();
        if (!conditions.isEmpty()) {
            request.put("filter", conditions);
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
