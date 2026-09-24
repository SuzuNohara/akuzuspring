package com.nexus.nexussync.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.MediatorClimate;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class MediatorViewTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

  private static Profile person(int id, int rating, int level) {
    Constraints c =
        new Constraints(
            "LOW", "NEAR", DayOfWeek.SATURDAY, LocalTime.of(17, 0), LocalTime.of(22, 0));
    return new Profile(
        id,
        "Tlalpan",
        Optional.of(new Location(19.29, -99.16)),
        Map.of("leer", level, "cocinar", 3),
        List.of(new EmotionEntry(TODAY.minusDays(1), "SAD", 5)),
        List.of(
            new HistoryEntry("cine", TODAY.minusDays(7), true, true, OptionalInt.of(rating)),
            new HistoryEntry("museo", TODAY.minusDays(7), true, false, OptionalInt.empty()),
            new HistoryEntry("viejo", TODAY.minusDays(200), true, true, OptionalInt.of(1))),
        c,
        Optional.of(Map.of(Feature.INTEREST, 0.9)));
  }

  private static Context context(MediatorClimate climate) {
    return ContextBuilder.build(
        person(8, 5, 4), person(4, 2, 1), TODAY, ContextBuilderTest.params(climate), Map.of());
  }

  // U3-10
  @Test
  void given_context_when_of_then_json_has_no_private_data() throws Exception {
    Map<String, Object> view =
        MediatorView.of(
            context(MediatorClimate.AGGREGATED),
            ContextBuilderTest.params(MediatorClimate.AGGREGATED));

    String json = new ObjectMapper().writeValueAsString(view);

    assertThat(json)
        .doesNotContain("emotional_recent")
        .doesNotContain("emotionalRecent")
        .doesNotContain("\"rating\"")
        .doesNotContain("truth_weights")
        .doesNotContain("truthWeights")
        .doesNotContain("\"location\"")
        .doesNotContain("\"history\"")
        .doesNotContain("SAD")
        .doesNotContain("19.29");
    assertThat(view.keySet())
        .containsExactly(
            "couple_id",
            "today",
            "windows",
            "preferences_a",
            "preferences_b",
            "climate_a",
            "climate_b",
            "history_agg");
  }

  // U3-10
  @Test
  void given_context_when_of_then_public_fields_are_aggregated() {
    Map<String, Object> view =
        MediatorView.of(
            context(MediatorClimate.AGGREGATED),
            ContextBuilderTest.params(MediatorClimate.AGGREGATED));

    assertThat(view)
        .containsEntry("couple_id", "4-8")
        .containsEntry("today", "2026-09-27")
        .containsEntry("preferences_a", Map.of("cocinar", 3, "leer", 4))
        .containsEntry("preferences_b", Map.of("cocinar", 3, "leer", 1))
        .containsEntry("climate_a", Map.of("valence", "LOW", "energy", "LOW"))
        .containsEntry(
            "windows",
            List.of(
                Map.of(
                    "day", "SATURDAY", "start", "17:00", "end", "22:00", "daypart", "AFTERNOON")))
        .containsEntry(
            "history_agg", Map.of("n_offered", 2, "n_chosen", 1, "n_rated", 2, "avg_rating", 3.5));
  }

  // U3-11
  @Test
  void given_mediator_climate_none_when_of_then_no_climate_keys() {
    Map<String, Object> view =
        MediatorView.of(
            context(MediatorClimate.NONE), ContextBuilderTest.params(MediatorClimate.NONE));

    assertThat(view).doesNotContainKeys("climate_a", "climate_b").containsKey("history_agg");
  }

  // U3-11
  @Test
  void given_no_ratings_when_of_then_avg_rating_is_absent() {
    Constraints c =
        new Constraints("LOW", "NEAR", DayOfWeek.SUNDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
    Profile a =
        new Profile(1, "X", Optional.empty(), Map.of(), List.of(), List.of(), c, Optional.empty());
    Profile b =
        new Profile(2, "X", Optional.empty(), Map.of(), List.of(), List.of(), c, Optional.empty());
    Context ctx =
        ContextBuilder.build(
            a, b, TODAY, ContextBuilderTest.params(MediatorClimate.NONE), Map.of());

    Map<String, Object> view =
        MediatorView.of(ctx, ContextBuilderTest.params(MediatorClimate.NONE));

    assertThat(view)
        .containsEntry("history_agg", Map.of("n_offered", 0, "n_chosen", 0, "n_rated", 0));
  }
}
