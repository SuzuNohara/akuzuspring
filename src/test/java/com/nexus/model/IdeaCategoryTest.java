package com.nexus.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RF-34: los {@code activity_type} del catalogo se agrupan en las 7 dimensiones del bienestar del
 * cuestionario de preferencias ({@code pref_categories}). Un tipo desconocido (catalogo real
 * futuro) o de tipo OTHER cae en {@link IdeaCategory#SOCIAL} (decision de la usuaria, 2026-10-16).
 */
class IdeaCategoryTest {

    /** Los tipos que hoy existen en el catalogo, mas CAREER (actividades de Profesional). */
    private static final Set<String> CATALOG_TYPES =
            Set.of(
                    "SPORT_SESSION", "WORKSHOP", "FILM", "COURSE", "DINNER", "CONCERT",
                    "BOARD_GAME", "READING", "TASTING", "OTHER", "WALK", "EXHIBITION_VISIT",
                    "PUZZLE", "COOKING", "VIDEOGAME", "MEDITATION", "STARGAZING", "PICNIC",
                    "GUIDED_TOUR", "SHOW", "SWIM", "CLIMB", "CYCLE", "HIKE", "BOAT_RIDE",
                    "COFFEE", "SHOPPING", "VOLUNTEERING", "PHOTO_WALK", "CAMPING", "CAREER");

    @Test
    @DisplayName("should offer exactly the 7 wellness dimensions of the questionnaire, in order")
    void shouldOfferExactlyTheSevenWellnessDimensionsInOrder() {
        List<String> labels = Arrays.stream(IdeaCategory.values()).map(IdeaCategory::label).toList();

        assertThat(labels)
                .containsExactly(
                        "Físico", "Emocional", "Social", "Intelectual", "Profesional", "Ambiental",
                        "Espiritual");
    }

    @Test
    @DisplayName("should list every catalog activity type in exactly one dimension")
    void shouldListEveryCatalogActivityTypeInExactlyOneDimension() {
        for (String type : CATALOG_TYPES) {
            long owners =
                    Arrays.stream(IdeaCategory.values())
                            .filter(c -> c.activityTypes().contains(type))
                            .count();
            assertThat(owners).as(type).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("should give every dimension at least one activity type")
    void shouldGiveEveryDimensionAtLeastOneActivityType() {
        for (IdeaCategory dimension : IdeaCategory.values()) {
            assertThat(dimension.activityTypes()).as(dimension.name()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("should place known types in their wellness dimension")
    void shouldPlaceKnownTypesInTheirWellnessDimension() {
        assertThat(IdeaCategory.ofActivityType("SWIM")).isEqualTo(IdeaCategory.FISICO);
        assertThat(IdeaCategory.ofActivityType("DINNER")).isEqualTo(IdeaCategory.EMOCIONAL);
        assertThat(IdeaCategory.ofActivityType("FILM")).isEqualTo(IdeaCategory.SOCIAL);
        assertThat(IdeaCategory.ofActivityType("READING")).isEqualTo(IdeaCategory.INTELECTUAL);
        assertThat(IdeaCategory.ofActivityType("CAREER")).isEqualTo(IdeaCategory.PROFESIONAL);
        assertThat(IdeaCategory.ofActivityType("PICNIC")).isEqualTo(IdeaCategory.AMBIENTAL);
        assertThat(IdeaCategory.ofActivityType("MEDITATION")).isEqualTo(IdeaCategory.ESPIRITUAL);
    }

    @Test
    @DisplayName("should fall back to SOCIAL for OTHER, unknown or missing types")
    void shouldFallBackToSocialForOtherUnknownOrMissingTypes() {
        assertThat(IdeaCategory.ofActivityType("OTHER")).isEqualTo(IdeaCategory.SOCIAL);
        assertThat(IdeaCategory.ofActivityType("KARAOKE_INVENTADO")).isEqualTo(IdeaCategory.SOCIAL);
        assertThat(IdeaCategory.ofActivityType(null)).isEqualTo(IdeaCategory.SOCIAL);
        assertThat(IdeaCategory.FALLBACK).isEqualTo(IdeaCategory.SOCIAL);
    }

    @Test
    @DisplayName("should give every dimension a distinct non blank spanish label")
    void shouldGiveEveryDimensionADistinctNonBlankSpanishLabel() {
        Set<String> labels =
                Arrays.stream(IdeaCategory.values())
                        .map(IdeaCategory::label)
                        .collect(Collectors.toSet());
        assertThat(labels).hasSize(IdeaCategory.values().length).doesNotContain("", " ");
    }

    @Test
    @DisplayName("should parse a dimension code case insensitively and reject unknown codes")
    void shouldParseADimensionCodeCaseInsensitivelyAndRejectUnknownCodes() {
        assertThat(IdeaCategory.parse("social")).isEqualTo(Optional.of(IdeaCategory.SOCIAL));
        assertThat(IdeaCategory.parse("PROFESIONAL")).isEqualTo(Optional.of(IdeaCategory.PROFESIONAL));
        assertThat(IdeaCategory.parse("CULTURA")).isEmpty();
        assertThat(IdeaCategory.parse(null)).isEmpty();
    }
}
