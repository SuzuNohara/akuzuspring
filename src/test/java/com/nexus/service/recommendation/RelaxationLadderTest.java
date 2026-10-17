package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.model.IdeaCategory;
import com.nexus.model.Scope;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * R16 - Escalera de filtros blandos: se amplian en orden fijo hasta juntar opciones. Nunca se
 * relajan la franja (RN-27) ni la distancia (RN-31).
 */
class RelaxationLadderTest {

    private static final Instant START = Instant.parse("2026-10-18T00:00:00Z");
    private static final Instant END = Instant.parse("2026-10-18T03:00:00Z");

    private static RecommendationQuery query(Scope scope, int maxCost, IdeaCategory category) {
        return new RecommendationQuery(scope, maxCost, category, START, END, List.of());
    }

    @Test
    @DisplayName("S6: primero estricto, luego sin categoria, sin momento del dia, presupuesto y alcance")
    void orderIsFixed() {
        List<LadderStep> steps = RelaxationLadder.steps(query(Scope.CITY, 10_000, IdeaCategory.SOCIAL));

        assertThat(steps.get(0).relaxed()).isEmpty();
        assertThat(steps.get(0).daypartRequired()).isTrue();
        assertThat(steps.get(0).query().category()).isEqualTo(IdeaCategory.SOCIAL);

        assertThat(steps.get(1).relaxed()).containsExactly(RelaxedFilter.CATEGORY);
        assertThat(steps.get(1).query().category()).isNull();

        assertThat(steps.get(2).relaxed()).containsExactlyInAnyOrder(RelaxedFilter.CATEGORY, RelaxedFilter.DAYPART);
        assertThat(steps.get(2).daypartRequired()).isFalse();

        LadderStep last = steps.get(steps.size() - 1);
        assertThat(last.relaxed()).isEqualTo(EnumSet.allOf(RelaxedFilter.class));
        assertThat(last.query().scope()).isEqualTo(Scope.ANY);
        assertThat(last.query().maxCostMxn()).isEqualTo(RecommendationQuery.MAX_COST_MXN);
    }

    @Test
    @DisplayName("el presupuesto sube por pasos hasta 15 000, sin quedarse en 0")
    void budgetGrowsUntilCap() {
        List<Integer> budgets =
                RelaxationLadder.steps(query(Scope.ANY, 0, null)).stream()
                        .filter(s -> s.relaxed().contains(RelaxedFilter.BUDGET))
                        .map(s -> s.query().maxCostMxn())
                        .toList();

        assertThat(budgets).isSorted().doesNotHaveDuplicates();
        assertThat(budgets.get(0)).isPositive();
        assertThat(budgets.get(budgets.size() - 1)).isEqualTo(RecommendationQuery.MAX_COST_MXN);
    }

    @Test
    @DisplayName("se saltan los pasos que no cambian nada (sin categoria, alcance ANY, tope maximo)")
    void skipsNoOpSteps() {
        List<LadderStep> steps =
                RelaxationLadder.steps(query(Scope.ANY, RecommendationQuery.MAX_COST_MXN, null));

        assertThat(steps).hasSize(2);
        assertThat(steps.get(1).relaxed()).containsExactly(RelaxedFilter.DAYPART);
    }

    @Test
    @DisplayName("la franja y las referencias nunca cambian")
    void slotAndReferencesStay() {
        for (LadderStep step : RelaxationLadder.steps(query(Scope.HOME, 500, IdeaCategory.FISICO))) {
            assertThat(step.query().slotStart()).isEqualTo(START);
            assertThat(step.query().slotEnd()).isEqualTo(END);
            assertThat(step.query().references()).isEmpty();
        }
    }
}
