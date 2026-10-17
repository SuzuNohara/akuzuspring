package com.nexus.service.recommendation;

import com.nexus.model.IdeaCategory;
import com.nexus.model.Scope;
import com.nexus.service.ProximityFilter.Coordinates;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Lo que se busca, solo con valores cerrados (RN-32). Las referencias son los puntos contra los que
 * se mide la distancia (RN-31): la ubicacion de quien pide o el centro de su alcaldia; vacia = sin
 * filtro de distancia. Nunca se guardan (RN-30).
 *
 * @param category dimension del bienestar; {@code null} = cualquiera
 */
public record RecommendationQuery(
        Scope scope,
        int maxCostMxn,
        IdeaCategory category,
        Instant slotStart,
        Instant slotEnd,
        List<Coordinates> references) {

    /** Tope del deslizador de presupuesto por persona. */
    public static final int MAX_COST_MXN = 15_000;

    public RecommendationQuery {
        references = List.copyOf(references);
    }

    /** Minutos de la franja libre. */
    public long slotMinutes() {
        return Duration.between(slotStart, slotEnd).toMinutes();
    }

    RecommendationQuery withCategory(IdeaCategory newCategory) {
        return new RecommendationQuery(scope, maxCostMxn, newCategory, slotStart, slotEnd, references);
    }

    RecommendationQuery withMaxCost(int newMaxCost) {
        return new RecommendationQuery(scope, newMaxCost, category, slotStart, slotEnd, references);
    }

    RecommendationQuery withScope(Scope newScope) {
        return new RecommendationQuery(newScope, maxCostMxn, category, slotStart, slotEnd, references);
    }
}
