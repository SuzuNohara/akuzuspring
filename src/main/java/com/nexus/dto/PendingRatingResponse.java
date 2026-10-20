package com.nexus.dto;

import java.time.Instant;

/**
 * CU42 - Cita que esta persona todavia puede calificar (Figma C12a).
 *
 * @param expiresAt fin de la ventana de 48 h (RN-36)
 */
public record PendingRatingResponse(
        Long eventId, String title, Instant startDateTime, Instant endDateTime, String location, Instant expiresAt) {}
