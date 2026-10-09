package com.nexus.agent.ai.catalog;

import java.util.Optional;
import java.util.Set;

/**
 * Una entrada del catálogo que se indexa (nexus-AGENT-00): una actividad en un lugar concreto (o en
 * casa). Es el payload tipado del índice; KB-02 mapea sus entidades a este record (D-10).
 */
public record CatalogEntry(
        String activityId,
        String placeId,
        String title,
        String description,
        String placeName,
        BankCategory bankCategory,
        WellnessDimension wellnessDimension,
        LocationScope locationScope,
        String borough,
        Double lat,
        Double lon,
        int costMin,
        int costMax,
        boolean isOutdoor,
        Set<WeatherOk> weatherOk,
        int durationMin,
        int durationAvg,
        int durationMax) {

    public CatalogEntry {
        weatherOk = weatherOk == null ? Set.of() : Set.copyOf(weatherOk);
    }

    /**
     * Primera razón por la que la entrada no debe indexarse (R24), o vacío si es válida: id o título
     * en blanco, costo negativo, {@code costMin > costMax}, duraciones que no cumplan {@code 0 < min
     * ≤ avg ≤ max}, o una actividad {@code CITY} sin coordenadas.
     */
    public Optional<String> invalidReason() {
        if (isBlank(activityId)) {
            return Optional.of("activityId en blanco");
        }
        if (isBlank(title)) {
            return Optional.of("title en blanco");
        }
        if (costMin < 0 || costMax < 0) {
            return Optional.of("costo negativo");
        }
        if (costMin > costMax) {
            return Optional.of("costMin > costMax");
        }
        if (!(0 < durationMin && durationMin <= durationAvg && durationAvg <= durationMax)) {
            return Optional.of("duraciones fuera de 0 < min <= avg <= max");
        }
        if (locationScope == LocationScope.CITY && (lat == null || lon == null)) {
            return Optional.of("CITY sin coordenadas");
        }
        return Optional.empty();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
