package com.nexus.service.recommendation.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.model.IdeaCategory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [t06b-agente-java] U2 - Texto canonico, hash, id del punto y nombre de la coleccion. */
class CanonicalTextTest {

    private static Offer.OfferBuilder home() {
        return Offer.builder()
                .activityId(12L)
                .title("Noche de juegos de mesa")
                .description("Sacar un juego de mesa para dos.")
                .rationale("Competencia amable sin salir de casa.")
                .activityType("BOARD_GAME")
                .dimension(IdeaCategory.SOCIAL)
                .interests(List.of("Juegos de mesa", "Estrategia"))
                .ambience(List.of("HOME", "SOCIAL"))
                .dayparts(List.of("NIGHT"))
                .locationScope("HOME")
                .priceBand("FREE")
                .costMxnPp(0)
                .weatherOk(List.of("ANY"))
                .durationMin(60)
                .durationAvg(120)
                .durationMax(180);
    }

    private static Offer city() {
        return home().placeId(40L)
                .locationScope("CITY")
                .placeName("Ludoteca Roma")
                .borough("Cuauhtémoc")
                .lat(19.41)
                .lon(-99.16)
                .priceBand("LOW")
                .costMxnPp(150)
                .build();
    }

    @Test
    @DisplayName("U2.1: should write the five fixed lines for an offer at home")
    void shouldWriteTheFiveFixedLinesForAnOfferAtHome() {
        String text = CanonicalText.of(home().build());

        assertThat(text).isEqualTo(
                "Actividad: Noche de juegos de mesa. Sacar un juego de mesa para dos.\n"
                        + "Tipo: board game. Bienestar: Bienestar Social.\n"
                        + "Intereses: Juegos de mesa, Estrategia. Ambiente: home, social.\n"
                        + "Lugar: en casa.\n"
                        + "Precio: gratis, 0 MXN por persona. Duración: 120 min (60 a 180).");
    }

    @Test
    @DisplayName("U2.2: should name the place and its borough for an offer in the city")
    void shouldNameThePlaceAndItsBoroughForAnOfferInTheCity() {
        String text = CanonicalText.of(city());

        assertThat(text.lines().toList().get(3)).isEqualTo("Lugar: Ludoteca Roma, Cuauhtémoc.");
        assertThat(text.lines().toList().get(4)).startsWith("Precio: bajo, 150 MXN por persona.");
    }

    @Test
    @DisplayName("U2.3: should write ninguno for empty lists")
    void shouldWriteNingunoForEmptyLists() {
        String text = CanonicalText.of(home().interests(List.of()).ambience(List.of()).build());

        assertThat(text.lines().toList().get(2)).isEqualTo("Intereses: ninguno. Ambiente: ninguno.");
    }

    @Test
    @DisplayName("U2.4: should hash the same inputs equally and change with model, version or text")
    void shouldHashTheSameInputsEquallyAndChangeWithModelVersionOrText() {
        String hash = CanonicalText.hash("bge-m3", "t2", "hola");

        assertThat(hash).isEqualTo("d700eb0f21921ec04a15017fe0314e1eae39f743a7f34c5f255c320735af41d3");
        assertThat(CanonicalText.hash("bge-m3", "t2", "hola")).isEqualTo(hash);
        assertThat(CanonicalText.hash("nomic", "t2", "hola")).isNotEqualTo(hash);
        assertThat(CanonicalText.hash("bge-m3", "t1", "hola")).isNotEqualTo(hash);
        assertThat(CanonicalText.hash("bge-m3", "t2", "adios")).isNotEqualTo(hash);
    }

    @Test
    @DisplayName("U2.5: should give a stable uuid5 that differs between home and a place")
    void shouldGiveAStableUuid5ThatDiffersBetweenHomeAndAPlace() {
        assertThat(CanonicalText.pointId(home().build())).isEqualTo("dcc3bc0e-d70f-5ce6-8a6d-57a3aeb1691f");
        assertThat(CanonicalText.pointId(city())).isEqualTo("ebaca0c4-7913-5968-a3b1-73d37cc537dd");
    }

    @Test
    @DisplayName("U2.6: should replace anything outside a-z, 0-9 and dash in the collection name")
    void shouldReplaceAnythingOutsideTheSlugAlphabetInTheCollectionName() {
        assertThat(CanonicalText.collectionName("bge-m3:latest", 1024, "t2"))
                .isEqualTo("nexus_catalog__bge-m3-latest__d1024__t2");
        assertThat(CanonicalText.collectionName("BGE-M3", 1024, "t2")).isEqualTo("nexus_catalog__bge-m3__d1024__t2");
    }

    @Test
    @DisplayName("U1: should key an offer by its activity and, in the city, its place")
    void shouldKeyAnOfferByItsActivityAndPlace() {
        assertThat(home().build().key()).isEqualTo("12");
        assertThat(city().key()).isEqualTo("12@40");
    }
}
