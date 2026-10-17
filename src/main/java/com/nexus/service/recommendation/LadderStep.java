package com.nexus.service.recommendation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Un escalon de {@link RelaxationLadder}: la consulta a usar, si se exige el momento del dia y que
 * filtros van ampliados hasta aqui.
 */
public record LadderStep(RecommendationQuery query, boolean daypartRequired, Set<RelaxedFilter> relaxed) {

    public LadderStep {
        EnumSet<RelaxedFilter> copy = EnumSet.noneOf(RelaxedFilter.class);
        copy.addAll(relaxed);
        relaxed = Collections.unmodifiableSet(copy);
    }

    LadderStep relax(RecommendationQuery newQuery, boolean newDaypartRequired, RelaxedFilter filter) {
        EnumSet<RelaxedFilter> more = EnumSet.noneOf(RelaxedFilter.class);
        more.addAll(relaxed);
        more.add(filter);
        return new LadderStep(newQuery, newDaypartRequired, more);
    }
}
