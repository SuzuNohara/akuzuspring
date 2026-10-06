package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** RF-34 - Una idea del banco tal como la ve la persona. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdeaResponse {
    private Long id;
    private String title;
    private String description;
    /** Codigo de {@link com.nexus.model.IdeaCategory}. */
    private String category;
    private String categoryLabel;
    /** true si se hace en casa (no requiere lugar). */
    private boolean atHome;
    private boolean outdoor;
    /** Duracion tipica en minutos; sirve para prellenar el evento (RF-35). */
    private Integer durationMinutes;
    /** {@code FREE}, {@code LOW}, {@code MID}, {@code HIGH} o {@code PREMIUM}. */
    private String priceBand;
    private Integer costPerPersonMxn;
}
