package com.nexus.service.recommendation;

import java.util.Set;

/**
 * Quien elige las actividades a recomendar. Hoy: {@link CatalogCandidateSource} (MySQL). Al
 * conectar el agente: busqueda por vectores en Qdrant, que cae al catalogo si falla.
 */
public interface CandidateSource {

    /**
     * Hasta {@code n} opciones que cumplen la franja y la distancia, ampliando filtros blandos si
     * hace falta (R16).
     *
     * @param excluded actividades que no deben salir (ya mostradas o descartadas)
     */
    Selection select(RecommendationQuery query, CoupleContext context, Set<Long> excluded, int n);
}
