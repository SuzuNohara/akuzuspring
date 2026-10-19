package com.nexus.service.recommendation;

import com.nexus.model.IdeaCategory;
import com.nexus.model.Scope;
import com.nexus.model.WeatherCondition;
import com.nexus.service.ProximityFilter.Coordinates;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Lo que se busca, solo con valores cerrados (RN-32). Las referencias son los puntos contra los que
 * se mide la distancia (RN-31): la ubicacion de quien pide o el centro de su alcaldia; vacia = sin
 * filtro de distancia. Nunca se guardan (RN-30).
 *
 * @param categories dimensiones del bienestar aceptadas; vacio = cualquiera (en pareja se pueden
 *     elegir varias)
 * @param weather clima previsto en la franja (CU40); {@link WeatherCondition#UNKNOWN} = sin
 *     pronostico, no descarta nada. Como la franja, nunca se relaja
 */
public record RecommendationQuery(
        Scope scope,
        int maxCostMxn,
        Set<IdeaCategory> categories,
        Instant slotStart,
        Instant slotEnd,
        List<Coordinates> references,
        WeatherCondition weather) {

    /** Tope del deslizador de presupuesto por persona. */
    public static final int MAX_COST_MXN = 15_000;

    public RecommendationQuery {
        references = List.copyOf(references);
        categories = categories == null ? Set.of() : Set.copyOf(categories);
        weather = weather == null ? WeatherCondition.UNKNOWN : weather;
    }

    /** Sin pronostico todavia; {@link #withWeather} lo agrega. */
    public RecommendationQuery(Scope scope, int maxCostMxn, Set<IdeaCategory> categories, Instant slotStart,
                               Instant slotEnd, List<Coordinates> references) {
        this(scope, maxCostMxn, categories, slotStart, slotEnd, references, WeatherCondition.UNKNOWN);
    }

    /** Modalidad individual: una sola dimensión o {@code null} = cualquiera. */
    public RecommendationQuery(Scope scope, int maxCostMxn, IdeaCategory category, Instant slotStart, Instant slotEnd,
                               List<Coordinates> references) {
        this(scope, maxCostMxn, category == null ? Set.of() : Set.of(category), slotStart, slotEnd, references);
    }

    /** Si la actividad entra en las dimensiones pedidas. */
    public boolean acceptsCategory(IdeaCategory category) {
        return categories.isEmpty() || categories.contains(category);
    }

    /** Minutos de la franja libre. */
    public long slotMinutes() {
        return Duration.between(slotStart, slotEnd).toMinutes();
    }

    /** La misma consulta con el clima previsto en la franja. */
    public RecommendationQuery withWeather(WeatherCondition newWeather) {
        return new RecommendationQuery(scope, maxCostMxn, categories, slotStart, slotEnd, references, newWeather);
    }

    RecommendationQuery withAnyCategory() {
        return new RecommendationQuery(scope, maxCostMxn, Set.<IdeaCategory>of(), slotStart, slotEnd, references, weather);
    }

    RecommendationQuery withMaxCost(int newMaxCost) {
        return new RecommendationQuery(scope, newMaxCost, categories, slotStart, slotEnd, references, weather);
    }

    RecommendationQuery withScope(Scope newScope) {
        return new RecommendationQuery(newScope, maxCostMxn, categories, slotStart, slotEnd, references, weather);
    }
}
