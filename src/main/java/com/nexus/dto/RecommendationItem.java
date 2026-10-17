package com.nexus.dto;

import com.nexus.model.ReasonSource;
import java.time.Instant;

/**
 * Una tarjeta de recomendacion. Nunca lleva datos de animo (RN-28) ni coordenadas.
 *
 * @param id id de la recomendacion (para descartar o invitar)
 * @param category codigo de la dimension ({@code IdeaCategory}); {@code categoryLabel} es el texto
 * @param scope {@code HOME} o {@code CITY}
 * @param placeName lugar en la ciudad; {@code null} en casa
 * @param startsAt inicio sugerido (inicio de la franja)
 * @param endsAt inicio + duracion promedio, sin pasar del fin de la franja
 */
public record RecommendationItem(
        Long id,
        Long activityId,
        String title,
        String category,
        String categoryLabel,
        String scope,
        String placeName,
        String boroughName,
        int durationMinutes,
        int costPerPersonMxn,
        String reason,
        ReasonSource reasonSource,
        Instant startsAt,
        Instant endsAt) {}
