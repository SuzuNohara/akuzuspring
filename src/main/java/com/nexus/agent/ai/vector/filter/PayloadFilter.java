package com.nexus.agent.ai.vector.filter;

import java.util.List;

/** Filtro de payload: condiciones que deben cumplirse ({@code must}) y que no ({@code mustNot}). */
public record PayloadFilter(List<FilterCondition> must, List<FilterCondition> mustNot) {

    public PayloadFilter {
        must = List.copyOf(must);
        mustNot = List.copyOf(mustNot);
    }

    public static PayloadFilter empty() {
        return new PayloadFilter(List.of(), List.of());
    }

    public boolean isEmpty() {
        return must.isEmpty() && mustNot.isEmpty();
    }
}
