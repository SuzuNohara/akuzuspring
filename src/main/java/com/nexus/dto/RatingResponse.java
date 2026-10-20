package com.nexus.dto;

import java.time.Instant;

/** CU42 - Confirmacion de que la calificacion se guardo. Nunca devuelve el puntaje (RN-37). */
public record RatingResponse(Long eventId, Instant ratedAt) {}
