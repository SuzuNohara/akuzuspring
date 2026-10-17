package com.nexus.service.recommendation;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;

/**
 * Momento del dia en el que empieza una franja, con los mismos valores que
 * {@code activities.dayparts}. Una actividad con {@code ANY} sirve a cualquier hora.
 */
public enum Daypart {
    MORNING,
    AFTERNOON,
    NIGHT;

    private static final String ANY = "ANY";
    private static final String SEPARATOR = "\\|";
    private static final int AFTERNOON_FROM_HOUR = 12;
    private static final int NIGHT_FROM_HOUR = 19;

    /** Momento del dia de un instante en la zona horaria dada. */
    public static Daypart of(Instant instant, ZoneId zone) {
        int hour = instant.atZone(zone).getHour();
        if (hour < AFTERNOON_FROM_HOUR) {
            return MORNING;
        }
        return hour < NIGHT_FROM_HOUR ? AFTERNOON : NIGHT;
    }

    /**
     * Si una actividad con estos {@code dayparts} se puede hacer en este momento. El catalogo separa
     * varios momentos con {@code |} (p. ej. {@code AFTERNOON|NIGHT}).
     */
    public boolean admits(String dayparts) {
        if (dayparts == null || ANY.equals(dayparts)) {
            return true;
        }
        return Arrays.asList(dayparts.split(SEPARATOR)).contains(name());
    }
}
