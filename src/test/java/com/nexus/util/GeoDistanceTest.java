package com.nexus.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import com.nexus.nexussync.places.Geo;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-31 - Haversine propio de la aplicacion (2.4.7), sin depender de nexussync. */
class GeoDistanceTest {

    private static final double ZOCALO_LAT = 19.4326;
    private static final double ZOCALO_LON = -99.1332;

    @Test
    @DisplayName("G1: should return zero for the same point")
    void shouldReturnZeroForTheSamePoint() {
        assertThat(GeoDistance.haversineKm(ZOCALO_LAT, ZOCALO_LON, ZOCALO_LAT, ZOCALO_LON)).isZero();
    }

    @Test
    @DisplayName("G2: should measure one degree of latitude as R * pi / 180")
    void shouldMeasureOneDegreeOfLatitudeAsAnArcOfTheEarthRadius() {
        double expected = GeoDistance.EARTH_RADIUS_KM * Math.PI / 180;

        assertThat(GeoDistance.haversineKm(0, 0, 1, 0)).isCloseTo(expected, offset(1e-9));
        assertThat(expected).isCloseTo(111.195, offset(0.001));
    }

    @Test
    @DisplayName("G2: should measure Zocalo to Coyoacan at about 9.6 km")
    void shouldMeasureZocaloToCoyoacanAtAbout9Point6Km() {
        assertThat(GeoDistance.haversineKm(ZOCALO_LAT, ZOCALO_LON, 19.3500, -99.1620))
                .isCloseTo(9.6, offset(0.1));
    }

    @Test
    @DisplayName("G3: should be symmetric")
    void shouldBeSymmetric() {
        double ab = GeoDistance.haversineKm(ZOCALO_LAT, ZOCALO_LON, 19.2571, -99.1036);
        double ba = GeoDistance.haversineKm(19.2571, -99.1036, ZOCALO_LAT, ZOCALO_LON);

        assertThat(ab).isEqualTo(ba);
    }

    /**
     * Garantia del cambio: la formula propia da lo mismo que la de nexussync a la que sustituye.
     * Esta prueba se borra junto con el paquete nexussync en la limpieza del cierre.
     */
    @Test
    @DisplayName("G4: should match the previous nexussync formula within 1 m on 20 CDMX pairs")
    void shouldMatchThePreviousFormulaWithinOneMetre() {
        Random random = new Random(20261016);
        for (int i = 0; i < 20; i++) {
            double lat1 = 19.1 + random.nextDouble() * 0.5;
            double lon1 = -99.35 + random.nextDouble() * 0.4;
            double lat2 = 19.1 + random.nextDouble() * 0.5;
            double lon2 = -99.35 + random.nextDouble() * 0.4;

            assertThat(GeoDistance.haversineKm(lat1, lon1, lat2, lon2))
                    .isCloseTo(Geo.haversineKm(lat1, lon1, lat2, lon2), offset(0.001));
        }
    }
}
