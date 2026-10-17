package com.nexus.dto.planning;

/** "Listo": la ubicacion solo con "Cerca de nosotros"; se usa al buscar y se olvida (RN-30). */
public record ReadyRequest(Double lat, Double lon) {}
