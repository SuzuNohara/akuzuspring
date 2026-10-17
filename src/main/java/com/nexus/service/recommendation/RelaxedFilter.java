package com.nexus.service.recommendation;

/**
 * R16 - Filtros blandos que se amplian, en este orden, cuando no alcanzan las opciones. La franja
 * (RN-27) y la distancia (RN-31) nunca se amplian.
 */
public enum RelaxedFilter {
    CATEGORY,
    DAYPART,
    BUDGET,
    SCOPE
}
