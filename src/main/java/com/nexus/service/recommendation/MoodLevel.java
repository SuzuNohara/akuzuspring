package com.nexus.service.recommendation;

import java.util.List;
import java.util.OptionalDouble;

/**
 * RN-28 - Nivel combinado de la pareja en un eje del animo (valencia o energia) de los ultimos 7
 * dias. Es lo unico del animo que llega al agente: nunca el valor de cada persona.
 */
public enum MoodLevel {
    BAJO,
    MEDIO,
    ALTO,
    SIN_DATO;

    /** Limite entre niveles sobre la escala -1.0 a 1.0 (tercios). */
    private static final double THIRD = 1.0 / 3;

    /** Nivel del promedio de los valores conocidos; {@link #SIN_DATO} si no hay ninguno. */
    public static MoodLevel combine(List<Double> values) {
        OptionalDouble mean = values.stream().mapToDouble(Double::doubleValue).average();
        if (mean.isEmpty()) {
            return SIN_DATO;
        }
        if (mean.getAsDouble() < -THIRD) {
            return BAJO;
        }
        return mean.getAsDouble() > THIRD ? ALTO : MEDIO;
    }
}
