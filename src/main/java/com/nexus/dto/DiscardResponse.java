package com.nexus.dto;

/**
 * Resultado de descartar una opcion: la que entra en lugar de la descartada, si queda alguna
 * guardada.
 *
 * @param replacement siguiente opcion no vista; {@code null} si ya no hay
 */
public record DiscardResponse(RecommendationItem replacement, boolean hasMore) {}
