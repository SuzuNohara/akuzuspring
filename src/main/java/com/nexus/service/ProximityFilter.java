package com.nexus.service;

import com.nexus.nexussync.places.Geo;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * RF-36 / RN-31 - Descarta actividades cuyo lugar queda a mas de {@code radiusKm} (15 km por
 * RN-31) de la pareja, con la formula de Haversine (2.4.7; la misma de nexussync, {@link Geo}).
 *
 * <p>Criterios:
 *
 * <ul>
 *   <li>El lugar debe quedar dentro del radio de <b>cada</b> integrante que compartio ubicacion:
 *       la cita es para los dos, no basta con que le quede cerca a uno.
 *   <li>RN-30: si nadie compartio ubicacion, no se filtra nada (el agente se puede usar sin ese
 *       permiso).
 *   <li>Una actividad sin lugar (p. ej. en casa) nunca se descarta.
 * </ul>
 *
 * <p>Pieza independiente a proposito: se conecta al motor de recomendacion (RF-33) cuando se
 * decida cual es, y a datos reales cuando exista el catalogo de lugares con coordenadas.
 */
public class ProximityFilter {

    /** RN-31. */
    public static final double DEFAULT_RADIUS_KM = 15.0;

    private final double radiusKm;

    public ProximityFilter(double radiusKm) {
        if (!(radiusKm > 0)) {
            throw new IllegalArgumentException("El radio debe ser positivo: " + radiusKm);
        }
        this.radiusKm = radiusKm;
    }

    /** Punto WGS84 en grados decimales. */
    public record Coordinates(double lat, double lon) {
        public Coordinates {
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                throw new IllegalArgumentException("Coordenadas fuera de rango: " + lat + ", " + lon);
            }
        }
    }

    public static double distanceKm(Coordinates a, Coordinates b) {
        return Geo.haversineKm(a.lat(), a.lon(), b.lat(), b.lon());
    }

    /**
     * @param items candidatos, en el orden en que se quieren conservar
     * @param locator lugar de cada candidato; vacio si no requiere lugar
     * @param memberLocations ubicaciones que compartieron los integrantes (0, 1 o 2)
     * @return los candidatos que cumplen RN-31, en el mismo orden
     */
    public <T> List<T> filter(
            List<T> items,
            Function<T, Optional<Coordinates>> locator,
            List<Coordinates> memberLocations) {
        if (memberLocations.isEmpty()) {
            return List.copyOf(items);
        }
        return items.stream()
                .filter(
                        item ->
                                locator.apply(item)
                                        .map(place -> withinRadiusOfAll(place, memberLocations))
                                        .orElse(true))
                .toList();
    }

    private boolean withinRadiusOfAll(Coordinates place, List<Coordinates> members) {
        return members.stream().allMatch(member -> distanceKm(place, member) <= radiusKm);
    }
}
