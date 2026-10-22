package com.nexus.service.recommendation.agent;

import com.nexus.config.AgentProperties;
import com.nexus.integration.ollama.OllamaClient;
import com.nexus.integration.qdrant.QdrantClient;
import com.nexus.integration.qdrant.QdrantPoint;
import com.nexus.service.recommendation.agent.OfferCatalog.CatalogLoad;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * [t06b-agente-java] U4 - Sube las ofertas del catalogo a Qdrant, un punto por oferta, y apunta el
 * alias de {@code nexus.agent.alias} a la coleccion. Es idempotente: solo vectoriza las ofertas cuyo
 * {@code text_hash} cambio y borra los puntos de ofertas que ya no existen (igual que
 * {@code indexer.py} del lab). Sustituye a {@code ActivityIndexer}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfferIndexer {

    /** Dimension de los vectores de {@code bge-m3}. */
    public static final int DIMENSION = 1024;

    static final int BATCH = 32;
    static final String TEXT_HASH = "text_hash";

    /** Indices de payload para los campos que filtran (U6). */
    static final Map<String, String> PAYLOAD_INDEXES = indexes();

    private final OfferCatalog catalog;
    private final OllamaClient ollama;
    private final QdrantClient qdrant;
    private final AgentProperties properties;

    /**
     * @param indexed ofertas vectorizadas y subidas en esta corrida
     * @param skippedUnchanged ofertas cuyo texto no cambio
     * @param deleted puntos borrados porque su oferta ya no existe
     * @param skippedInvalid ofertas que el catalogo descarto
     */
    public record IndexReport(
            String collection, int indexed, int skippedUnchanged, int deleted, int skippedInvalid, long elapsedMs) {}

    private record Pending(String id, String text, Map<String, Object> payload) {}

    /**
     * Deja la coleccion igual al catalogo de MySQL y mueve el alias.
     *
     * @implNote O(o) ofertas, con O(c / 32) llamadas a Ollama para las c que cambiaron.
     */
    public IndexReport ensureIndexed() {
        long start = System.nanoTime();
        CatalogLoad load = catalog.load();
        String collection = CanonicalText.collectionName(properties.embedModel(), DIMENSION, properties.textVersion());
        qdrant.ensureCollection(collection, DIMENSION);
        qdrant.ensurePayloadIndexes(collection, PAYLOAD_INDEXES);
        Map<String, String> existing = qdrant.payloadValues(collection, TEXT_HASH);

        List<Pending> pending = new ArrayList<>();
        Set<String> current = new HashSet<>();
        for (Offer offer : load.offers()) {
            String id = CanonicalText.pointId(offer);
            String text = CanonicalText.of(offer);
            String hash = CanonicalText.hash(properties.embedModel(), properties.textVersion(), text);
            current.add(id);
            if (!hash.equals(existing.get(id))) {
                pending.add(new Pending(id, text, payload(offer, hash)));
            }
        }
        upsertInBatches(collection, pending);
        List<String> stale = existing.keySet().stream().filter(id -> !current.contains(id)).sorted().toList();
        if (!stale.isEmpty()) {
            qdrant.delete(collection, stale);
        }
        qdrant.pointAlias(properties.alias(), collection);

        IndexReport report = new IndexReport(
                collection,
                pending.size(),
                load.offers().size() - pending.size(),
                stale.size(),
                load.skipped().size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        log.info("[t06b] Catalogo indexado: {}", report);
        return report;
    }

    private void upsertInBatches(String collection, List<Pending> pending) {
        for (int from = 0; from < pending.size(); from += BATCH) {
            List<Pending> batch = pending.subList(from, Math.min(from + BATCH, pending.size()));
            List<float[]> vectors = ollama.embed(batch.stream().map(Pending::text).toList());
            List<QdrantPoint> points = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                points.add(new QdrantPoint(batch.get(i).id(), checked(vectors.get(i)), batch.get(i).payload()));
            }
            qdrant.upsert(collection, points);
        }
    }

    private float[] checked(float[] vector) {
        if (vector.length != DIMENSION) {
            throw new IllegalStateException("El modelo " + properties.embedModel() + " da vectores de "
                    + vector.length + "; la coleccion espera " + DIMENSION);
        }
        return vector;
    }

    /** Atributos del punto con los que se filtra la busqueda; {@code location} solo en la ciudad. */
    static Map<String, Object> payload(Offer o, String textHash) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activity_id", o.activityId());
        payload.put("dimension", o.dimension().name());
        payload.put("location_scope", o.locationScope());
        payload.put("cost_mxn_pp", o.costMxnPp());
        payload.put("duration_min", o.durationMin());
        payload.put("dayparts", o.dayparts());
        payload.put("is_outdoor", o.outdoor());
        payload.put("weather_ok", o.weatherOk());
        if (o.placeId() != null) {
            payload.put("place_id", o.placeId());
            payload.put("place_name", o.placeName());
            payload.put("borough", o.borough());
            payload.put("location", Map.of("lat", o.lat(), "lon", o.lon()));
        }
        payload.put(TEXT_HASH, textHash);
        return payload;
    }

    private static Map<String, String> indexes() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("activity_id", "integer");
        schema.put("dimension", "keyword");
        schema.put("location_scope", "keyword");
        schema.put("cost_mxn_pp", "integer");
        schema.put("duration_min", "integer");
        schema.put("dayparts", "keyword");
        schema.put("is_outdoor", "bool");
        schema.put("weather_ok", "keyword");
        schema.put("location", "geo");
        return Collections.unmodifiableMap(schema);
    }
}
