package com.nexus.integration.qdrant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [T-06] U4 - Condiciones que debe cumplir una actividad candidata dentro de Qdrant. Un campo nulo
 * o vacio no restringe nada.
 *
 * @param scopes valores permitidos de {@code location_scope} (HOME, CITY)
 * @param priceBands bandas de precio permitidas (RN de presupuesto)
 * @param activityTypes tipos de actividad de la categoria elegida
 * @param daypart momento del dia en que empieza la franja (MORNING, AFTERNOON, NIGHT)
 * @param maxDurationMin duracion minima de la actividad que todavia cabe en la franja (RN-27)
 * @param excludedIds actividades ya mostradas o descartadas en la solicitud (RN-39)
 */
public record QdrantFilter(
        Set<String> scopes,
        Set<String> priceBands,
        Set<String> activityTypes,
        String daypart,
        Integer maxDurationMin,
        Set<Long> excludedIds) {

    /** Filtro en el formato de la API REST de Qdrant ({@code must} / {@code must_not}). */
    public Map<String, Object> toJson() {
        List<Map<String, Object>> must = new ArrayList<>();
        matchAny(must, "location_scope", scopes);
        matchAny(must, "price_band", priceBands);
        matchAny(must, "activity_type", activityTypes);
        if (daypart != null) {
            must.add(Map.of("key", "dayparts", "match", Map.of("value", daypart)));
        }
        if (maxDurationMin != null) {
            must.add(Map.of("key", "duration_min", "range", Map.of("lte", maxDurationMin)));
        }

        Map<String, Object> filter = new LinkedHashMap<>();
        if (!must.isEmpty()) {
            filter.put("must", must);
        }
        if (excludedIds != null && !excludedIds.isEmpty()) {
            filter.put("must_not", List.of(Map.of("has_id", List.copyOf(excludedIds))));
        }
        return filter;
    }

    private static void matchAny(List<Map<String, Object>> must, String key, Set<String> values) {
        if (values != null && !values.isEmpty()) {
            must.add(Map.of("key", key, "match", Map.of("any", List.copyOf(values))));
        }
    }
}
