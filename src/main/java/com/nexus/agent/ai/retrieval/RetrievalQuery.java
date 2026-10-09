package com.nexus.agent.ai.retrieval;

import com.nexus.agent.ai.catalog.BankCategory;
import com.nexus.agent.ai.vector.filter.FilterCondition;
import java.util.List;
import java.util.Set;

/**
 * Entrada cerrada de una recomendación (nexus-AGENT-00, RN-32): las señales de preferencia de cada
 * integrante y los filtros de categoría, ámbito, presupuesto por persona, tiempo disponible,
 * actividades a excluir y condiciones extra. {@code category} en {@code null} significa "Todas".
 */
public record RetrievalQuery(
        List<PreferenceSignal> personA,
        List<PreferenceSignal> personB,
        BankCategory category,
        ScopeFilter scope,
        Integer maxCostPerPersonMxn,
        Integer slotMinutes,
        Set<String> excludedActivityIds,
        List<FilterCondition> extraConditions) {

    public RetrievalQuery {
        personA = List.copyOf(personA);
        personB = List.copyOf(personB);
        excludedActivityIds = Set.copyOf(excludedActivityIds);
        extraConditions = List.copyOf(extraConditions);
    }
}
