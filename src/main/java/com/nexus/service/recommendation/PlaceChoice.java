package com.nexus.service.recommendation;

/**
 * Lugar elegido para una actividad en la ciudad: el mas cercano que cumple la distancia (RN-31).
 *
 * @param distanceKm distancia a la referencia mas lejana; {@code null} si no hubo referencias
 */
public record PlaceChoice(Long placeId, String name, String borough, Double distanceKm) {}
