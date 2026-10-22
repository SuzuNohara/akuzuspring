package com.nexus.integration.qdrant;

import com.nexus.model.WeatherCondition;
import com.nexus.service.ProximityFilter.Coordinates;
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
 * @param within [t06b-agente-java] referencias de la pareja; la oferta debe quedar a
 *     {@code radiusM} de cada una (RN-31). Si {@code scopes} admite HOME, las de casa tambien pasan
 * @param radiusM radio en metros de cada referencia de {@code within}
 * @param weather [t06b-agente-java] clima de la franja: pasan las de bajo techo y las de aire libre
 *     cuyo {@code weather_ok} lo incluye o es ANY; {@link WeatherCondition#UNKNOWN} no restringe
 * @param excludedActivityIds [t06b-agente-java] actividades ya mostradas, descartadas o elegidas,
 *     por {@code activity_id} (una actividad tiene varios puntos, uno por lugar)
 */
public record QdrantFilter(
        Set<String> scopes,
        Integer maxCostMxn,
        Set<String> dimensions,
        String daypart,
        Integer maxDurationMin,
        List<Coordinates> within,
        int radiusM,
        WeatherCondition weather,
        Set<Long> excludedActivityIds) {

    /** Valor de {@code dayparts} que sirve a cualquier hora. */
    public static final String ANY_DAYPART = "ANY";

    /** Sin ninguna condicion. */
    public static final QdrantFilter NONE = new QdrantFilter(null, null, null, null, null, null, 0, null, null);

    private static final String HOME = "HOME";

    /**
     * Filtro en el formato de la API REST de Qdrant ({@code must} / {@code must_not}).
     *
     * @implNote O(c) con c condiciones.
     */
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
        addDistance(must);
        addWeather(must);

        Map<String, Object> filter = new LinkedHashMap<>();
        if (!must.isEmpty()) {
            filter.put("must", must);
        }
        List<Map<String, Object>> mustNot = mustNot();
        if (!mustNot.isEmpty()) {
            filter.put("must_not", mustNot);
        }
        return filter;
    }

    /** Un {@code geo_radius} por referencia; si se admiten las de casa, cada uno va en un {@code should} con HOME. */
    private void addDistance(List<Map<String, Object>> must) {
        if (within == null) {
            return;
        }
        boolean admitsHome = scopes == null || scopes.isEmpty() || scopes.contains(HOME);
        for (Coordinates reference : within) {
            Map<String, Object> geo = Map.of(
                    "key", "location",
                    "geo_radius", Map.of(
                            "center", Map.of("lat", reference.lat(), "lon", reference.lon()),
                            "radius", radiusM));
            must.add(admitsHome ? Map.of("should", List.of(matchValue("location_scope", HOME), geo)) : geo);
        }
    }

    private void addWeather(List<Map<String, Object>> must) {
        if (weather == null || weather == WeatherCondition.UNKNOWN) {
            return;
        }
        must.add(Map.of(
                "should",
                List.of(
                        matchValue("is_outdoor", false),
                        Map.of("key", "weather_ok", "match", Map.of("any", List.of(weather.name(), "ANY"))))));
    }

    private List<Map<String, Object>> mustNot() {
        List<Map<String, Object>> mustNot = new ArrayList<>();
        if (excludedActivityIds != null && !excludedActivityIds.isEmpty()) {
            mustNot.add(Map.of("key", "activity_id", "match", Map.of("any", List.copyOf(excludedActivityIds))));
        }
        return mustNot;
    }

    private static Map<String, Object> matchValue(String key, Object value) {
        return Map.of("key", key, "match", Map.of("value", value));
    }

    private static void matchAny(List<Map<String, Object>> must, String key, Set<String> values) {
        if (values != null && !values.isEmpty()) {
            must.add(Map.of("key", key, "match", Map.of("any", List.copyOf(values))));
        }
    }
}
