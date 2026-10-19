package com.nexus.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

/**
 * Clima de una franja para decidir si una actividad al aire libre es viable (CU40, seccion
 * "Informacion meteorologica"). Va de mejor a peor; {@link #UNKNOWN} significa sin pronostico y
 * nunca descarta nada.
 */
public enum WeatherCondition {
    SUNNY,
    CLOUDY,
    RAINY,
    UNKNOWN;

    /** Desde esta probabilidad de lluvia la hora cuenta como lluviosa aunque el cielo este despejado. */
    public static final int RAIN_PROBABILITY_THRESHOLD = 50;

    /** Codigos WMO de nubes y niebla. */
    private static final Set<Integer> CLOUD_CODES = Set.of(2, 3, 45, 48);

    /** Valor de {@code activities.weather_ok} que acepta cualquier clima. */
    private static final String ANY = "ANY";

    /**
     * Condicion de una hora del pronostico.
     *
     * @param wmoCode codigo de tiempo WMO (0 despejado ... 95-99 tormenta)
     * @param rainProbability probabilidad de lluvia en %, 0-100
     */
    public static WeatherCondition ofHour(int wmoCode, int rainProbability) {
        if (rainProbability >= RAIN_PROBABILITY_THRESHOLD || wmoCode >= 51) {
            return RAINY; // 51+ = llovizna, lluvia, nieve, chubascos y tormenta
        }
        return CLOUD_CODES.contains(wmoCode) ? CLOUDY : SUNNY;
    }

    /** La peor hora de la franja; sin horas, {@link #UNKNOWN}. */
    public static WeatherCondition worst(Collection<WeatherCondition> hours) {
        return hours.stream()
                .filter(h -> h != UNKNOWN)
                .max(Comparator.naturalOrder())
                .orElse(UNKNOWN);
    }

    /**
     * Si una actividad cabe con este clima: las de bajo techo siempre; las de aire libre solo si su
     * {@code weather_ok} es ANY o incluye esta condicion.
     */
    public boolean allows(boolean outdoor, String weatherOk) {
        if (!outdoor || this == UNKNOWN || weatherOk == null || weatherOk.isBlank()) {
            return true;
        }
        return Arrays.stream(weatherOk.split("\\|"))
                .map(String::trim)
                .anyMatch(ok -> ok.equals(ANY) || ok.equals(name()));
    }
}
