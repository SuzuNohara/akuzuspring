package com.nexus.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RF-31 - Registro de estado emocional. valence/activation son las coordenadas del modelo
 * circunflejo del afecto (2.7.1, Russell 1980): -1.0 a 1.0 en ambos ejes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEmotionRequest {

    @NotNull(message = "La valencia es obligatoria")
    @DecimalMin(value = "-1.0", message = "La valencia debe estar entre -1.0 y 1.0")
    @DecimalMax(value = "1.0", message = "La valencia debe estar entre -1.0 y 1.0")
    private Double valence;

    @NotNull(message = "La activación es obligatoria")
    @DecimalMin(value = "-1.0", message = "La activación debe estar entre -1.0 y 1.0")
    @DecimalMax(value = "1.0", message = "La activación debe estar entre -1.0 y 1.0")
    private Double activation;

    @Size(max = 40, message = "La etiqueta no puede tener más de 40 caracteres")
    private String label;
}
