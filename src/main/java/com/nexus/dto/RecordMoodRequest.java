package com.nexus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de {@code PUT /mood/today/{userId}} (nexus-EMO-01, CU31). Solo opciones cerradas (RN-32):
 * el código debe ser mayúsculas y guion bajo, de 1 a 32 caracteres. El catálogo se valida después
 * en el servicio.
 */
public record RecordMoodRequest(
        @NotBlank @Pattern(regexp = "^[A-Z_]{1,32}$") String code) {}
