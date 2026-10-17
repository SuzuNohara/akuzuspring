package com.nexus.dto;

import com.nexus.model.Scope;
import com.nexus.model.ZoneType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * CU36 - Lo que pide la persona en la modalidad individual. Solo valores cerrados (RN-32); la
 * ubicacion ({@code lat}/{@code lon}) solo con {@link ZoneType#NEAR_US} y nunca se guarda (RN-30).
 *
 * @param boroughCode codigo de alcaldia, obligatorio con {@link ZoneType#BOROUGH}
 * @param maxCostMxn tope por persona del deslizador, de 0 (solo gratis) a 15 000
 * @param category dimension del bienestar ({@code IdeaCategory}); {@code null} = cualquiera
 */
public record RecommendationParams(
        @NotNull ZoneType zoneType,
        String boroughCode,
        Double lat,
        Double lon,
        @NotNull Scope scope,
        @Min(0) @Max(15_000) int maxCostMxn,
        String category,
        @NotNull Instant slotStart,
        @NotNull Instant slotEnd) {}
