package com.nexus.nexussync.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests de {@link CsvSets}: sets serializados como {@code A|B|C} (kb/schema.md §7). */
class CsvSetsTest {

  // U2-02
  @Test
  void given_pipe_separated_values_when_parsed_then_each_value_is_an_element() {
    Set<String> parsed = CsvSets.parse("AFTERNOON|MORNING|NIGHT");

    assertThat(parsed).containsExactlyInAnyOrder("AFTERNOON", "MORNING", "NIGHT");
  }

  // U2-02
  @Test
  void given_single_value_when_parsed_then_singleton_set() {
    assertThat(CsvSets.parse("ANY")).containsExactly("ANY");
  }

  // U2-02
  @Test
  void given_spaces_around_separators_when_parsed_then_values_are_trimmed() {
    Set<String> parsed = CsvSets.parse("  PARK | GARDEN  ");

    assertThat(parsed).containsExactlyInAnyOrder("PARK", "GARDEN");
  }

  // U2-02
  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "|", " | | "})
  void given_empty_or_separator_only_text_when_parsed_then_empty_set(String text) {
    assertThat(CsvSets.parse(text)).isEmpty();
  }

  // U2-02
  @Test
  void given_duplicated_values_when_parsed_then_deduplicated() {
    assertThat(CsvSets.parse("PARK|PARK|GARDEN")).containsExactlyInAnyOrder("PARK", "GARDEN");
  }

  // U2-02
  @Test
  void given_unsorted_values_when_parsed_then_iteration_order_is_alphabetical() {
    assertThat(CsvSets.parse("SUNNY|CLOUDY|ANY")).containsExactly("ANY", "CLOUDY", "SUNNY");
  }

  // U2-02
  @Test
  void given_parsed_set_when_mutated_then_unsupported() {
    Set<String> parsed = CsvSets.parse("PARK|GARDEN");

    assertThatThrownBy(() -> parsed.add("LAKE")).isInstanceOf(UnsupportedOperationException.class);
  }

  // U2-02
  @Test
  void given_null_when_parsed_then_null_pointer_exception() {
    assertThatThrownBy(() -> CsvSets.parse(null)).isInstanceOf(NullPointerException.class);
  }
}
