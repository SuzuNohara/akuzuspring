package com.nexus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** CU42 - Calificacion de 1 (muy mal) a 5 (muy bien). */
public record RateRequest(@NotNull @Min(1) @Max(5) Integer score) {}
