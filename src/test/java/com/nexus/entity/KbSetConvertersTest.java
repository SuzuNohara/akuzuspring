package com.nexus.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Ida y vuelta y orden de los convertidores de conjuntos de enum (nexus-KB-02, R7). */
class KbSetConvertersTest {

    private final AmbienceSetConverter ambience = new AmbienceSetConverter();
    private final WeatherOkSetConverter weather = new WeatherOkSetConverter();

    @Test
    @DisplayName("should read pipe separated values into a set")
    void shouldReadPipeSeparatedValues() {
        assertThat(ambience.convertToEntityAttribute("NATURE|QUIET"))
                .containsExactlyInAnyOrder(Ambience.NATURE, Ambience.QUIET);
    }

    @Test
    @DisplayName("should return an empty set for an empty column")
    void shouldReturnEmptySetForEmptyColumn() {
        assertThat(ambience.convertToEntityAttribute("")).isEmpty();
        assertThat(ambience.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    @DisplayName("should write the set sorted and pipe separated")
    void shouldWriteSortedPipeSeparated() {
        assertThat(ambience.convertToDatabaseColumn(Set.of(Ambience.QUIET, Ambience.NATURE)))
                .isEqualTo("NATURE|QUIET");
        assertThat(weather.convertToDatabaseColumn(Set.of(WeatherOk.SUNNY, WeatherOk.ANY)))
                .isEqualTo("ANY|SUNNY");
    }

    @Test
    @DisplayName("should write an empty string for a null or empty set")
    void shouldWriteEmptyForNullSet() {
        assertThat(ambience.convertToDatabaseColumn(null)).isEmpty();
        assertThat(ambience.convertToDatabaseColumn(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("should reject an unknown value")
    void shouldRejectUnknownValue() {
        assertThatThrownBy(() -> weather.convertToEntityAttribute("SUNNY|HURRICANE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WeatherOk");
    }
}
