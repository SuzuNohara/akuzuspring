package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.service.ProximityFilter.Coordinates;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RF-36 / RN-31: se descartan actividades a mas de 15 km (Haversine) de la pareja. RN-30: si
 * nadie compartio ubicacion, no se filtra nada (el agente debe poder usarse sin permiso).
 */
class ProximityFilterTest {

    /** Lugar de prueba: nombre + coordenadas opcionales (las actividades en casa no tienen). */
    private record Spot(String name, Optional<Coordinates> where) {}

    // Puntos reales de la CDMX; distancias aproximadas por Haversine.
    private static final Coordinates ZOCALO = new Coordinates(19.4326, -99.1332);
    private static final Coordinates COYOACAN = new Coordinates(19.3500, -99.1620); // ~9.6 km del Zocalo
    private static final Coordinates XOCHIMILCO = new Coordinates(19.2571, -99.1036); // ~19.7 km
    private static final Coordinates SATELITE = new Coordinates(19.5097, -99.2337); // ~13.4 km

    private final ProximityFilter filter = new ProximityFilter(15.0);

    private List<String> names(List<Spot> spots) {
        return spots.stream().map(Spot::name).toList();
    }

    @Test
    @DisplayName("should compute haversine distance within 100 m of the reference value")
    void shouldComputeHaversineDistanceWithin100mOfTheReferenceValue() {
        assertThat(ProximityFilter.distanceKm(ZOCALO, COYOACAN)).isCloseTo(9.6, org.assertj.core.data.Offset.offset(0.1));
        assertThat(ProximityFilter.distanceKm(ZOCALO, ZOCALO)).isZero();
    }

    @Test
    @DisplayName("should drop places farther than the radius from the couple (RN-31)")
    void shouldDropPlacesFartherThanTheRadiusFromTheCouple() {
        List<Spot> spots =
                List.of(
                        new Spot("coyoacan", Optional.of(COYOACAN)),
                        new Spot("xochimilco", Optional.of(XOCHIMILCO)));

        List<Spot> kept = filter.filter(spots, Spot::where, List.of(ZOCALO));

        assertThat(names(kept)).containsExactly("coyoacan");
    }

    @Test
    @DisplayName("should require the place to be within the radius of both members")
    void shouldRequireThePlaceToBeWithinTheRadiusOfBothMembers() {
        // Satelite queda a ~13 km del Zocalo pero a mas de 15 km de Coyoacan.
        List<Spot> spots = List.of(new Spot("satelite", Optional.of(SATELITE)));

        assertThat(filter.filter(spots, Spot::where, List.of(ZOCALO))).hasSize(1);
        assertThat(filter.filter(spots, Spot::where, List.of(ZOCALO, COYOACAN))).isEmpty();
    }

    @Test
    @DisplayName("should always keep activities without a place, like the ones at home")
    void shouldAlwaysKeepActivitiesWithoutAPlace() {
        List<Spot> spots = List.of(new Spot("en-casa", Optional.empty()));

        assertThat(names(filter.filter(spots, Spot::where, List.of(ZOCALO)))).containsExactly("en-casa");
    }

    @Test
    @DisplayName("should not filter anything when nobody shared a location (RN-30)")
    void shouldNotFilterAnythingWhenNobodySharedALocation() {
        List<Spot> spots =
                List.of(
                        new Spot("xochimilco", Optional.of(XOCHIMILCO)),
                        new Spot("en-casa", Optional.empty()));

        assertThat(filter.filter(spots, Spot::where, List.of())).hasSize(2);
    }

    @Test
    @DisplayName("should keep a place exactly on the radius")
    void shouldKeepAPlaceExactlyOnTheRadius() {
        double radius = ProximityFilter.distanceKm(ZOCALO, COYOACAN);
        ProximityFilter exact = new ProximityFilter(radius);

        assertThat(exact.filter(List.of(new Spot("borde", Optional.of(COYOACAN))), Spot::where, List.of(ZOCALO)))
                .hasSize(1);
    }

    @Test
    @DisplayName("should reject a non positive radius and invalid coordinates")
    void shouldRejectANonPositiveRadiusAndInvalidCoordinates() {
        assertThatThrownBy(() -> new ProximityFilter(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coordinates(91, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coordinates(0, 181)).isInstanceOf(IllegalArgumentException.class);
    }
}
