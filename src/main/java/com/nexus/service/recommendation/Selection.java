package com.nexus.service.recommendation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Resultado de {@link CandidateSource#select}: las opciones en orden, que filtros hubo que ampliar
 * para juntarlas (se le avisa a la persona, R16) y de donde salieron.
 */
public record Selection(List<Candidate> candidates, Set<RelaxedFilter> relaxed, SelectionSource source) {

    public Selection {
        candidates = List.copyOf(candidates);
        EnumSet<RelaxedFilter> copy = EnumSet.noneOf(RelaxedFilter.class);
        copy.addAll(relaxed);
        relaxed = Collections.unmodifiableSet(copy);
    }
}
