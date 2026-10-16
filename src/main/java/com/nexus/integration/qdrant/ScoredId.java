package com.nexus.integration.qdrant;

/** [T-06] Resultado de una busqueda: id de la actividad y su similitud con la consulta. */
public record ScoredId(long id, double score) {}
