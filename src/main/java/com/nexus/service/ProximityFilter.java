package com.nexus.service;

import com.nexus.exception.BadRequestException;
import com.nexus.util.GeoDistance;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * RF-36 / RN-31 - Descarta actividades cuyo lugar queda a mas de {@code radiusKm} (15 km por
 * RN-31) de los puntos de referencia, con la formula de Haversine (2.4.7, {@link GeoDistance}).
 *
 * <p>Criterios:
 *
 * <ul>
 *   <li>Los puntos de referencia son la ubicacion de cada integrante que la compartio ("cerca de
 *       nosotros") o el centro de la alcaldia elegida. El lugar debe quedar dentro del radio de
 *       <b>cada</b> punto: la cita es para los dos, no basta con que le quede cerca a uno.
 *   <li>RN-30: sin puntos de referencia no se filtra nada (el agente se puede usar sin ese
 *       permiso).
 *   <li>Una actividad sin lugar (p. ej. en casa) nunca se descarta.
 * </ul>
 */
public class ProximityFilter {

    /** RN-31. */
    public static final double DEFAULT_RADIUS_KM = 15.0;

    /** [rango-distancia] Distancias que puede elegir la pareja (valores cerrados, RN-32). */
    public static final List<Integer> RADIUS_OPTIONS_KM = List.of(1, 3, 5, 10, 15);

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

    /**
     * [rango-distancia] Distancia pedida: sin valor, {@value #DEFAULT_RADIUS_KM} km (la app vieja no
     * la manda); un valor fuera de {@link #RADIUS_OPTIONS_KM} se rechaza.
     */
    public static int radiusOrDefault(Integer km) {
        if (km == null) {
            return (int) DEFAULT_RADIUS_KM;
        }
        if (!RADIUS_OPTIONS_KM.contains(km)) {
            throw new BadRequestException("Distancia no válida");
        }
        return km;
    }

    public static double distanceKm(Coordinates a, Coordinates b) {
        return GeoDistance.haversineKm(a.lat(), a.lon(), b.lat(), b.lon());
    }

    /**
     * @param items candidatos, en el orden en que se quieren conservar
     * @param locator lugar de cada candidato; vacio si no requiere lugar
     * @param memberLocations puntos de referencia: ubicaciones compartidas (0, 1 o 2) o el centro de
     *     la alcaldia elegida
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
