package com.nexus.agent.ai.support;

import com.nexus.agent.ai.vector.CollectionSpec;
import com.nexus.agent.ai.vector.ScoredPoint;
import com.nexus.agent.ai.vector.VectorPoint;
import com.nexus.agent.ai.vector.VectorStore;
import com.nexus.agent.ai.vector.VectorStoreException;
import com.nexus.agent.ai.vector.filter.FilterCondition;
import com.nexus.agent.ai.vector.filter.PayloadFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Doble en memoria de {@link VectorStore} para AGENT-02 y los tests del motor (nexus-AGENT-00, §4).
 * Coseno exacto; evalúa {@code Match}, {@code MatchAny} (también sobre listas del payload), {@code
 * Range}, {@code IsNull}, {@code AnyOf} y {@code GeoRadius} (Haversine, R = 6 371 008.8 m); agrupa
 * por {@code groupBy} quedándose con el mejor punto de cada grupo. {@link #calls()} cuenta llamadas.
 */
public final class InMemoryVectorStore implements VectorStore {

    private static final double EARTH_RADIUS_M = 6_371_008.8;

    private final Map<String, Map<String, VectorPoint>> collections = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final List<String> calls = new ArrayList<>();

    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public void ensureCollection(CollectionSpec spec) {
        calls.add("ensureCollection:" + spec.name());
        collections.computeIfAbsent(spec.name(), k -> new LinkedHashMap<>());
    }

    @Override
    public void upsert(String collection, List<VectorPoint> points) {
        calls.add("upsert:" + collection + ":" + points.size());
        Map<String, VectorPoint> c = collections.computeIfAbsent(collection, k -> new LinkedHashMap<>());
        for (VectorPoint p : points) {
            c.put(p.id(), p);
        }
    }

    @Override
    public void delete(String collection, Collection<String> ids) {
        calls.add("delete:" + collection + ":" + ids.size());
        Map<String, VectorPoint> c = collections.get(collection);
        if (c != null) {
            ids.forEach(c::remove);
        }
    }

    @Override
    public Map<String, String> payloadValues(String collection, String field) {
        calls.add("payloadValues:" + collection + ":" + field);
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, VectorPoint> c = collections.get(collection);
        if (c != null) {
            for (VectorPoint p : c.values()) {
                Object v = p.payload().get(field);
                if (v != null) {
                    out.put(p.id(), String.valueOf(v));
                }
            }
        }
        return out;
    }

    @Override
    public List<ScoredPoint> searchGrouped(
            String collectionOrAlias, float[] vector, PayloadFilter filter, String groupBy, int limit) {
        calls.add("searchGrouped:" + collectionOrAlias + ":" + groupBy + ":" + limit);
        String name = aliases.getOrDefault(collectionOrAlias, collectionOrAlias);
        Map<String, VectorPoint> c = collections.get(name);
        if (c == null) {
            throw new VectorStoreException("colección inexistente: " + name);
        }
        Map<String, ScoredPoint> bestByGroup = new LinkedHashMap<>();
        for (VectorPoint p : c.values()) {
            if (!matches(filter, p.payload())) {
                continue;
            }
            double score = cosine(vector, p.vector());
            String group = String.valueOf(p.payload().get(groupBy));
            ScoredPoint current = bestByGroup.get(group);
            if (current == null || score > current.score()) {
                bestByGroup.put(group, new ScoredPoint(p.id(), score, p.payload()));
            }
        }
        List<ScoredPoint> ranked = new ArrayList<>(bestByGroup.values());
        ranked.sort(Comparator.comparingDouble(ScoredPoint::score).reversed());
        return ranked.size() > limit ? new ArrayList<>(ranked.subList(0, limit)) : ranked;
    }

    @Override
    public void pointAlias(String alias, String collection) {
        calls.add("pointAlias:" + alias + "->" + collection);
        aliases.put(alias, collection);
    }

    // --- filtros ---------------------------------------------------------------------------

    private boolean matches(PayloadFilter filter, Map<String, Object> payload) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        for (FilterCondition c : filter.must()) {
            if (!eval(c, payload)) {
                return false;
            }
        }
        for (FilterCondition c : filter.mustNot()) {
            if (eval(c, payload)) {
                return false;
            }
        }
        return true;
    }

    private boolean eval(FilterCondition condition, Map<String, Object> payload) {
        if (condition instanceof FilterCondition.Match m) {
            return equalsOrContains(payload.get(m.key()), m.value());
        }
        if (condition instanceof FilterCondition.MatchAny m) {
            return m.values().stream().anyMatch(v -> equalsOrContains(payload.get(m.key()), v));
        }
        if (condition instanceof FilterCondition.Range r) {
            return inRange(payload.get(r.key()), r.gte(), r.lte());
        }
        if (condition instanceof FilterCondition.IsNull n) {
            return payload.get(n.key()) == null;
        }
        if (condition instanceof FilterCondition.AnyOf a) {
            return a.options().stream().anyMatch(o -> eval(o, payload));
        }
        if (condition instanceof FilterCondition.GeoRadius g) {
            return withinRadius(payload.get(g.key()), g);
        }
        throw new IllegalStateException("condición no soportada: " + condition);
    }

    private boolean equalsOrContains(Object payloadValue, Object wanted) {
        if (payloadValue instanceof Collection<?> list) {
            return list.stream().anyMatch(e -> Objects.equals(String.valueOf(e), String.valueOf(wanted)));
        }
        return Objects.equals(String.valueOf(payloadValue), String.valueOf(wanted));
    }

    private boolean inRange(Object payloadValue, Double gte, Double lte) {
        if (!(payloadValue instanceof Number n)) {
            return false;
        }
        double v = n.doubleValue();
        if (gte != null && v < gte) {
            return false;
        }
        return lte == null || !(v > lte);
    }

    @SuppressWarnings("unchecked")
    private boolean withinRadius(Object geo, FilterCondition.GeoRadius g) {
        double lat;
        double lon;
        if (geo instanceof Map<?, ?> map) {
            lat = toDouble(((Map<String, Object>) map).get("lat"));
            lon = toDouble(((Map<String, Object>) map).get("lon"));
        } else if (geo instanceof List<?> coords && coords.size() == 2) {
            lat = toDouble(coords.get(0));
            lon = toDouble(coords.get(1));
        } else {
            return false;
        }
        return haversineMeters(g.lat(), g.lon(), lat, lon) <= g.radiusMeters();
    }

    private static double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new VectorStoreException("dimensiones distintas: " + a.length + " vs " + b.length);
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** Vista inmutable de los alias registrados (para aserciones de test). */
    public Map<String, String> aliases() {
        return Collections.unmodifiableMap(aliases);
    }
}
