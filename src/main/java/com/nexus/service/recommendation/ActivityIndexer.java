package com.nexus.service.recommendation;

import com.nexus.config.AgentProperties;
import com.nexus.entity.Activity;
import com.nexus.integration.ollama.OllamaClient;
import com.nexus.integration.qdrant.QdrantClient;
import com.nexus.integration.qdrant.QdrantPoint;
import com.nexus.model.IdeaCategory;
import com.nexus.repository.ActivityRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * [T-06] U5 - Sube el catalogo de actividades a Qdrant: un vector por actividad (texto con el
 * prefijo {@code search_document:} que pide {@code nomic-embed-text}) y los atributos con los que
 * se filtra la busqueda.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityIndexer {

    /** Prefijo de documentos de {@code nomic-embed-text}; las consultas usan {@code search_query:}. */
    static final String DOCUMENT_PREFIX = "search_document: ";

    private static final int BATCH = 32;

    private final ActivityRepository activities;
    private final OllamaClient ollama;
    private final QdrantClient qdrant;
    private final AgentProperties properties;

    /** Si Qdrant tiene tantas actividades como MySQL (R9). */
    public boolean isInSync() {
        return qdrant.count(properties.collection()) == activities.count();
    }

    /** Reindexa solo si los conteos no coinciden; regresa cuantas subio (0 si no hizo falta). */
    public int ensureIndexed() {
        return isInSync() ? 0 : reindexAll();
    }

    /**
     * Vectoriza y sube todo el catalogo.
     *
     * @implNote O(a) llamadas a Ollama con a actividades (~90; 89 ms cada una en caliente).
     */
    public int reindexAll() {
        List<Activity> catalog = activities.findAll();
        List<QdrantPoint> batch = new ArrayList<>();
        boolean collectionReady = false;
        for (Activity activity : catalog) {
            float[] vector = ollama.embed(documentText(activity));
            if (!collectionReady) {
                qdrant.ensureCollection(properties.collection(), vector.length);
                collectionReady = true;
            }
            batch.add(new QdrantPoint(activity.getId(), vector, payload(activity)));
            if (batch.size() == BATCH) {
                qdrant.upsert(properties.collection(), List.copyOf(batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            qdrant.upsert(properties.collection(), List.copyOf(batch));
        }
        log.info("[T-06] Catalogo indexado en Qdrant: {} actividades", catalog.size());
        return catalog.size();
    }

    /** Texto que describe la actividad: titulo, descripcion, justificacion, ambiente y dimension. */
    public static String documentText(Activity a) {
        String place = "HOME".equals(a.getLocationScope()) ? "en casa" : "en la ciudad";
        String setting = Boolean.TRUE.equals(a.getOutdoor()) ? "al aire libre" : "bajo techo";
        String dimension = "Bienestar " + IdeaCategory.ofActivityType(a.getActivityType()).label();
        return DOCUMENT_PREFIX
                + Stream.of(a.getTitle(), a.getDescription(), a.getRationale(), place + ", " + setting, dimension)
                        .filter(Objects::nonNull)
                        .map(String::strip)
                        .filter(s -> !s.isEmpty())
                        .map(s -> s.endsWith(".") ? s.substring(0, s.length() - 1) : s)
                        .collect(Collectors.joining(". "))
                + ".";
    }

    static Map<String, Object> payload(Activity a) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activity_type", a.getActivityType());
        payload.put("dimension", IdeaCategory.ofActivityType(a.getActivityType()).name());
        payload.put("location_scope", a.getLocationScope());
        payload.put("cost_mxn_pp", a.getCostMxnPp());
        payload.put("duration_min", a.getDurationMin());
        payload.put("dayparts", a.getDayparts() == null ? List.of("ANY") : Arrays.asList(a.getDayparts().split("\\|")));
        payload.put("is_outdoor", Boolean.TRUE.equals(a.getOutdoor()));
        return payload;
    }
}
