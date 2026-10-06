package com.nexus.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RF-34: los 30 {@code activity_type} del catalogo se agrupan en pocas categorias legibles para
 * la persona. Un tipo desconocido (catalogo real futuro) nunca rompe: cae en {@link
 * IdeaCategory#OTRAS}.
 */
class IdeaCategoryTest {

    /** Los tipos que hoy existen en el catalogo (kb/schema.md, enum activity_type). */
    private static final Set<String> CATALOG_TYPES =
            Set.of(
                    "SPORT_SESSION", "WORKSHOP", "FILM", "COURSE", "DINNER", "CONCERT",
                    "BOARD_GAME", "READING", "TASTING", "OTHER", "WALK", "EXHIBITION_VISIT",
                    "PUZZLE", "COOKING", "VIDEOGAME", "MEDITATION", "STARGAZING", "PICNIC",
                    "GUIDED_TOUR", "SHOW", "SWIM", "CLIMB", "CYCLE", "HIKE", "BOAT_RIDE",
                    "COFFEE", "SHOPPING", "VOLUNTEERING", "PHOTO_WALK", "CAMPING");

    @Test
    @DisplayName("should map every catalog activity type to exactly one category")
    void shouldMapEveryCatalogActivityTypeToExactlyOneCategory() {
        for (String type : CATALOG_TYPES) {
            long owners =
                    Arrays.stream(IdeaCategory.values())
                            .filter(c -> c.activityTypes().contains(type))
                            .count();
            assertThat(owners).as(type).isLessThanOrEqualTo(1);
            assertThat(IdeaCategory.ofActivityType(type)).as(type).isNotNull();
        }
    }

    @Test
    @DisplayName("should place known types in their friendly category")
    void shouldPlaceKnownTypesInTheirFriendlyCategory() {
        assertThat(IdeaCategory.ofActivityType("DINNER")).isEqualTo(IdeaCategory.COMER_Y_BEBER);
        assertThat(IdeaCategory.ofActivityType("FILM")).isEqualTo(IdeaCategory.CULTURA);
        assertThat(IdeaCategory.ofActivityType("PICNIC")).isEqualTo(IdeaCategory.AIRE_LIBRE);
        assertThat(IdeaCategory.ofActivityType("SWIM")).isEqualTo(IdeaCategory.DEPORTE);
        assertThat(IdeaCategory.ofActivityType("WORKSHOP")).isEqualTo(IdeaCategory.APRENDER);
        assertThat(IdeaCategory.ofActivityType("BOARD_GAME")).isEqualTo(IdeaCategory.JUEGOS_Y_RELAX);
    }

    @Test
    @DisplayName("should fall back to OTRAS for unknown or missing types")
    void shouldFallBackToOtrasForUnknownOrMissingTypes() {
        assertThat(IdeaCategory.ofActivityType("KARAOKE_INVENTADO")).isEqualTo(IdeaCategory.OTRAS);
        assertThat(IdeaCategory.ofActivityType(null)).isEqualTo(IdeaCategory.OTRAS);
    }

    @Test
    @DisplayName("should give every category a non blank spanish label")
    void shouldGiveEveryCategoryANonBlankSpanishLabel() {
        Set<String> labels =
                Arrays.stream(IdeaCategory.values())
                        .map(IdeaCategory::label)
                        .collect(Collectors.toSet());
        assertThat(labels).hasSize(IdeaCategory.values().length).doesNotContain("", " ");
    }

    @Test
    @DisplayName("should parse a category code case insensitively and reject unknown codes")
    void shouldParseACategoryCodeCaseInsensitivelyAndRejectUnknownCodes() {
        assertThat(IdeaCategory.parse("cultura")).isEqualTo(Optional.of(IdeaCategory.CULTURA));
        assertThat(IdeaCategory.parse("CULTURA")).isEqualTo(Optional.of(IdeaCategory.CULTURA));
        assertThat(IdeaCategory.parse("nada")).isEmpty();
        assertThat(IdeaCategory.parse(null)).isEmpty();
    }
}
