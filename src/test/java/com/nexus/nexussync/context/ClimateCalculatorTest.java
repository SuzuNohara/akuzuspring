package com.nexus.nexussync.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClimateCalculatorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
  private static final int WINDOW = 7;

  private static EmotionEntry entry(int daysAgo, String code, int intensity) {
    return new EmotionEntry(TODAY.minusDays(daysAgo), code, intensity);
  }

  // U3-03
  @Test
  void given_happy_and_calm_when_of_then_valence_high_energy_mid() {
    List<EmotionEntry> entries = List.of(entry(1, "HAPPY", 5), entry(2, "CALM", 4));

    assertThat(ClimateCalculator.meanValence(entries, TODAY, WINDOW).getAsDouble())
        .isCloseTo(0.78, within(0.01));
    assertThat(ClimateCalculator.of(entries, TODAY, WINDOW))
        .isEqualTo(new Climate(Level.HIGH, Level.MID));
  }

  // U3-03
  @Test
  void given_sad_and_tired_when_of_then_low_low() {
    List<EmotionEntry> entries = List.of(entry(0, "SAD", 4), entry(3, "TIRED", 5));

    assertThat(ClimateCalculator.of(entries, TODAY, WINDOW))
        .isEqualTo(new Climate(Level.LOW, Level.LOW));
  }

  // U3-03
  @Test
  void given_happy_and_sad_equal_intensity_when_of_then_valence_zero_mid() {
    List<EmotionEntry> entries = List.of(entry(1, "HAPPY", 3), entry(1, "SAD", 3));

    assertThat(ClimateCalculator.meanValence(entries, TODAY, WINDOW).getAsDouble())
        .isCloseTo(0.0, within(1e-9));
    assertThat(ClimateCalculator.of(entries, TODAY, WINDOW).valence()).isEqualTo(Level.MID);
  }

  // U3-03
  @Test
  void given_angry_when_of_then_valence_low_energy_high() {
    List<EmotionEntry> entries = List.of(entry(1, "ANGRY", 2), entry(1, "STRESSED", 2));

    assertThat(ClimateCalculator.meanEnergy(entries, TODAY, WINDOW).getAsDouble())
        .isCloseTo(0.8, within(1e-9));
    assertThat(ClimateCalculator.of(entries, TODAY, WINDOW))
        .isEqualTo(new Climate(Level.LOW, Level.HIGH));
  }

  // U3-04
  @Test
  void given_no_entries_when_of_then_unknown() {
    assertThat(ClimateCalculator.of(List.of(), TODAY, WINDOW))
        .isEqualTo(new Climate(Level.UNKNOWN, Level.UNKNOWN));
    assertThat(ClimateCalculator.meanValence(List.of(), TODAY, WINDOW)).isEmpty();
    assertThat(ClimateCalculator.meanEnergy(List.of(), TODAY, WINDOW)).isEmpty();
  }

  // U3-04
  @Test
  void given_entry_exactly_window_days_ago_when_of_then_included() {
    List<EmotionEntry> entries = List.of(entry(WINDOW, "HAPPY", 5));

    assertThat(ClimateCalculator.of(entries, TODAY, WINDOW).valence()).isEqualTo(Level.HIGH);
  }

  // U3-04
  @Test
  void given_entries_older_than_window_or_future_when_of_then_unknown() {
    List<EmotionEntry> entries =
        List.of(entry(WINDOW + 1, "HAPPY", 5), new EmotionEntry(TODAY.plusDays(1), "SAD", 5));

    assertThat(ClimateCalculator.of(entries, TODAY, WINDOW))
        .isEqualTo(new Climate(Level.UNKNOWN, Level.UNKNOWN));
  }

  // U3-04
  @Test
  void given_intensity_out_of_range_when_of_then_entry_ignored() {
    List<EmotionEntry> entries = List.of(entry(1, "HAPPY", 0), entry(1, "SAD", 6));

    assertThat(ClimateCalculator.of(entries, TODAY, WINDOW).energy()).isEqualTo(Level.UNKNOWN);
  }

  // U3-05
  @Test
  void given_unknown_code_when_of_then_ignored_and_counted() {
    List<EmotionEntry> entries =
        List.of(entry(1, "EUPHORIC", 5), entry(1, " happy ", 2), entry(30, "BORED", 3));

    assertThat(ClimateCalculator.unknownCodes(entries, TODAY, WINDOW)).isEqualTo(1);
    assertThat(ClimateCalculator.of(entries, TODAY, WINDOW))
        .isEqualTo(new Climate(Level.HIGH, Level.HIGH));
  }

  // U3-05
  @Test
  void given_only_unknown_codes_when_of_then_unknown() {
    List<EmotionEntry> entries = List.of(entry(1, "EUPHORIC", 5));

    assertThat(ClimateCalculator.of(entries, TODAY, WINDOW).valence()).isEqualTo(Level.UNKNOWN);
  }

  @Test
  void given_threshold_boundaries_when_level_of_then_mid_inclusive() {
    assertThat(ClimateCalculator.levelOf(0.33)).isEqualTo(Level.MID);
    assertThat(ClimateCalculator.levelOf(-0.33)).isEqualTo(Level.MID);
    assertThat(ClimateCalculator.levelOf(0.331)).isEqualTo(Level.HIGH);
    assertThat(ClimateCalculator.levelOf(-0.331)).isEqualTo(Level.LOW);
  }

  @Test
  void given_every_code_when_parse_then_found_and_neutral_is_mid() {
    for (EmotionCode code : EmotionCode.values()) {
      assertThat(EmotionCode.parse(code.name().toLowerCase(java.util.Locale.ROOT))).contains(code);
    }
    List<EmotionEntry> neutral = List.of(entry(0, "NEUTRAL", 3), entry(0, "GRATEFUL", 1));
    assertThat(ClimateCalculator.of(neutral, TODAY, WINDOW))
        .isEqualTo(new Climate(Level.MID, Level.MID));
  }
}
