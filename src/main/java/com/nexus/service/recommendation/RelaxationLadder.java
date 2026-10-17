package com.nexus.service.recommendation;

import com.nexus.model.Scope;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * R16 - Nunca se muestran 0 recomendaciones: si con los filtros de la persona no alcanzan, se
 * amplian en orden fijo (categoria, momento del dia, presupuesto por pasos, alcance). Los pasos que
 * no cambian nada se omiten.
 */
public final class RelaxationLadder {

    /** Cada paso de presupuesto sube al menos esto, para no quedarse en $0. */
    private static final int MIN_BUDGET_STEP_MXN = 200;

    private RelaxationLadder() {}

    /**
     * Escalones en el orden en que se prueban; el primero es la consulta tal cual.
     *
     * @implNote O(log(MAX_COST_MXN)) escalones: el presupuesto crece en proporcion 1.5.
     */
    public static List<LadderStep> steps(RecommendationQuery q) {
        List<LadderStep> steps = new ArrayList<>();
        LadderStep current = new LadderStep(q, true, EnumSet.noneOf(RelaxedFilter.class));
        steps.add(current);
        if (!q.categories().isEmpty()) {
            current = current.relax(current.query().withAnyCategory(), true, RelaxedFilter.CATEGORY);
            steps.add(current);
        }
        current = current.relax(current.query(), false, RelaxedFilter.DAYPART);
        steps.add(current);
        int budget = q.maxCostMxn();
        while (budget < RecommendationQuery.MAX_COST_MXN) {
            budget = nextBudget(budget);
            current = current.relax(current.query().withMaxCost(budget), false, RelaxedFilter.BUDGET);
            steps.add(current);
        }
        if (q.scope() != Scope.ANY) {
            current = current.relax(current.query().withScope(Scope.ANY), false, RelaxedFilter.SCOPE);
            steps.add(current);
        }
        return List.copyOf(steps);
    }

    private static int nextBudget(int budget) {
        int grown = Math.max((int) Math.ceil(budget * 1.5), budget + MIN_BUDGET_STEP_MXN);
        return Math.min(grown, RecommendationQuery.MAX_COST_MXN);
    }
}
