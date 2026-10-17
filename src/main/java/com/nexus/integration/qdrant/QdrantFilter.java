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
 * @param maxCostMxn tope de costo por persona ({@code cost_mxn_pp}), del deslizador de presupuesto
 * @param dimensions dimensiones del bienestar aceptadas ({@code IdeaCategory})
 * @param daypart momento del dia en que empieza la franja (MORNING, AFTERNOON, NIGHT); tambien
 *     pasan las actividades marcadas {@code ANY}
 * @param maxDurationMin duracion minima de la actividad que todavia cabe en la franja (RN-27)
 * @param excludedIds actividades ya mostradas, descartadas o elegidas en la solicitud
 */
public record QdrantFilter(
        Set<String> scopes,
        Integer maxCostMxn,
        Set<String> dimensions,
        String daypart,
        Integer maxDurationMin,
        Set<Long> excludedIds) {

    /** Valor de {@code dayparts} que sirve a cualquier hora. */
    public static final String ANY_DAYPART = "ANY";

    /** Filtro en el formato de la API REST de Qdrant ({@code must} / {@code must_not}). */
    public Map<String, Object> toJson() {
        List<Map<String, Object>> must = new ArrayList<>();
        matchAny(must, "location_scope", scopes);
        if (maxCostMxn != null) {
            must.add(Map.of("key", "cost_mxn_pp", "range", Map.of("lte", maxCostMxn)));
        }
        matchAny(must, "dimension", dimensions);
        if (daypart != null) {
            must.add(Map.of("key", "dayparts", "match", Map.of("any", List.of(daypart, ANY_DAYPART))));
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
