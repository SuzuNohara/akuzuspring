package com.nexus.service.recommendation.agent;

import com.nexus.integration.qdrant.QdrantFilter;
import com.nexus.model.Scope;
import com.nexus.service.ProximityFilter;
import com.nexus.service.recommendation.Daypart;
import com.nexus.service.recommendation.LadderStep;
import com.nexus.service.recommendation.RecommendationQuery;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * [t06b-agente-java] U6 - Traduce un escalon de la {@code RelaxationLadder} al filtro duro de Qdrant,
 * con las mismas reglas que {@code CatalogCandidateSource} (R12): alcance, presupuesto, dimension,
 * momento del dia (si el escalon lo exige), que la duracion minima quepa en la franja, distancia
 * (RN-31), clima (CU40) y actividades ya usadas. Franja, distancia y clima nunca se relajan.
 */
public final class HardFilter {

    /** Radio de cada referencia en metros (15 km). */
    static final int RADIUS_M = (int) Math.round(ProximityFilter.DEFAULT_RADIUS_KM * 1000);

    private static final Set<String> ANY_SCOPE = Set.of(Scope.HOME.name(), Scope.CITY.name());

    private HardFilter() {}

    /**
     * @param daypart momento del dia en que empieza la franja
     * @param excluded actividades ya mostradas, descartadas o elegidas
     * @implNote O(r + e) con r referencias y e excluidas.
     */
    public static QdrantFilter of(LadderStep step, Daypart daypart, Set<Long> excluded) {
        RecommendationQuery q = step.query();
        Set<String> scopes = q.scope() == Scope.ANY ? ANY_SCOPE : Set.of(q.scope().name());
        Set<String> dimensions = q.categories().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
        return new QdrantFilter(
                scopes,
                q.maxCostMxn(),
                dimensions,
                step.daypartRequired() ? daypart.name() : null,
                (int) q.slotMinutes(),
                null,
                q.references(),
                RADIUS_M,
                q.weather(),
                excluded);
    }
}
