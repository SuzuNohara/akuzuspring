package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.dto.RecommendationItem;
import com.nexus.entity.Activity;
import com.nexus.entity.Place;
import com.nexus.entity.Recommendation;
import com.nexus.entity.RecommendationRequest;
import com.nexus.model.ReasonSource;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La tarjeta dice la duracion que cabe en el horario elegido, no el promedio del catalogo.
 * [kb-lugares] U8: y los datos del lugar; el evento guarda su ubicacion completa.
 */
class RecommendationItemTest {

    private static final Instant START = Instant.parse("2026-10-20T01:00:00Z");

    private static RecommendationItem itemFor(int avgMinutes, Duration slot) {
        Activity activity = Activity.builder().id(1L).title("Escalada en muro").activityType("SPORT")
                .locationScope("CITY").durationMin(60).durationAvg(avgMinutes).costMxnPp(300).build();
        RecommendationRequest request = RecommendationRequest.builder().id(9L)
                .slotStart(START).slotEnd(START.plus(slot)).build();
        return RecommendationService.toItem(Recommendation.builder().id(5L).request(request).activity(activity)
                .reason("frase").reasonSource(ReasonSource.CATALOG).build());
    }

    @Test
    @DisplayName("una actividad de 1 h 30 en un hueco de 1 h dice 60 min y termina con el hueco")
    void clampedToSlot() {
        RecommendationItem item = itemFor(90, Duration.ofHours(1));

        assertThat(item.durationMinutes()).isEqualTo(60);
        assertThat(item.endsAt()).isEqualTo(START.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("si cabe completa, conserva su duracion")
    void fitsWhole() {
        assertThat(itemFor(45, Duration.ofHours(2)).durationMinutes()).isEqualTo(45);
    }

    // [kb-lugares] U8 - Datos del lugar en la tarjeta y en el evento.

    private static Place cineteca() {
        return Place.builder().id(30L).name("Cineteca Nacional").borough("Coyoacán")
                .address("Av. México Coyoacán 389").neighborhood("Xoco")
                .website("https://www.cinetecanacional.net").phone("55 4155 1200").build();
    }

    private static RecommendationItem itemAt(Place place) {
        Activity activity = Activity.builder().id(1L).title("Cine").activityType("FILM")
                .locationScope(place == null ? "HOME" : "CITY").durationMin(60).durationAvg(90).costMxnPp(100).build();
        RecommendationRequest request = RecommendationRequest.builder().id(9L)
                .slotStart(START).slotEnd(START.plus(Duration.ofHours(2))).build();
        return RecommendationService.toItem(Recommendation.builder().id(5L).request(request).activity(activity)
                .place(place).reason("frase").reasonSource(ReasonSource.CATALOG).build());
    }

    @Test
    @DisplayName("[kb-lugares] P8.1: la tarjeta lleva direccion, colonia, web y telefono; en casa ninguno")
    void cardCarriesPlaceDetails() {
        RecommendationItem city = itemAt(cineteca());
        RecommendationItem home = itemAt(null);

        assertThat(city.placeAddress()).isEqualTo("Av. México Coyoacán 389");
        assertThat(city.placeNeighborhood()).isEqualTo("Xoco");
        assertThat(city.placeWebsite()).isEqualTo("https://www.cinetecanacional.net");
        assertThat(city.placePhone()).isEqualTo("55 4155 1200");
        assertThat(home.placeAddress()).isNull();
        assertThat(home.placeNeighborhood()).isNull();
        assertThat(home.placeWebsite()).isNull();
        assertThat(home.placePhone()).isNull();
    }

    @Test
    @DisplayName("[kb-lugares] P8.2: el evento dice nombre · direccion, colonia · alcaldia y omite lo que falte")
    void eventLocationJoinsWhatExists() {
        Place noAddress = Place.builder().name("Parque").borough("Tlalpan").neighborhood("Centro").build();
        Place bare = Place.builder().name("Cineteca").borough("Coyoacán").build();

        assertThat(RecommendationService.eventLocation(cineteca()))
                .isEqualTo("Cineteca Nacional · Av. México Coyoacán 389, Xoco · Coyoacán");
        assertThat(RecommendationService.eventLocation(noAddress)).isEqualTo("Parque · Centro · Tlalpan");
        assertThat(RecommendationService.eventLocation(bare)).isEqualTo("Cineteca · Coyoacán");
        assertThat(RecommendationService.eventLocation(null)).isEqualTo("En casa");
    }

    @Test
    @DisplayName("[kb-lugares] P8.3: si no cabe, quita la colonia y luego corta la direccion, nunca el nombre")
    void eventLocationFitsTheColumn() {
        String name = "N".repeat(160);
        Place withoutHood = Place.builder().name(name).borough("Gustavo A. Madero")
                .address("A".repeat(200)).neighborhood("C".repeat(200)).build();
        Place cut = Place.builder().name(name).borough("Gustavo A. Madero")
                .address("A".repeat(400)).neighborhood("C".repeat(60)).build();

        String first = RecommendationService.eventLocation(withoutHood);
        String second = RecommendationService.eventLocation(cut);

        assertThat(first).hasSizeLessThanOrEqualTo(RecommendationService.EVENT_LOCATION_MAX)
                .startsWith(name + " · " + "A".repeat(200)).doesNotContain("C").endsWith(" · Gustavo A. Madero");
        assertThat(second).hasSize(RecommendationService.EVENT_LOCATION_MAX)
                .startsWith(name + " · AAA").contains("…").doesNotContain("C").endsWith(" · Gustavo A. Madero");
    }
}
