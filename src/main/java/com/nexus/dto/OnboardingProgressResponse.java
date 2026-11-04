package com.nexus.dto;

/**
 * [primeros-pasos] Avance de los hitos de la primera configuracion (tarjeta "Primeros pasos").
 *
 * <p>De la pareja solo viaja si/no y su nombre: nunca que calendario ni que horarios (R6).
 *
 * @param me hitos individuales de quien pregunta
 * @param partner hitos individuales de la pareja; {@code null} sin vinculo activo
 * @param couple hitos de pareja; en {@code false} sin vinculo activo
 */
public record OnboardingProgressResponse(Mine me, Partner partner, Couple couple) {

    /** Hitos individuales de quien pregunta. */
    public record Mine(boolean questionnaire, boolean linked, boolean calendar, boolean schedule) {}

    /** Hitos individuales de la pareja, sin detalles. */
    public record Partner(String name, boolean questionnaire, boolean calendar, boolean schedule) {}

    /** Hitos que cuentan para los dos. */
    public record Couple(boolean firstRecommendation, boolean firstRatedDate) {}
}
