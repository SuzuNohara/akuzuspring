package com.nexus.nexussync.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.catalog.Daypart;
import com.nexus.nexussync.params.AgentsParams;
import com.nexus.nexussync.params.ContextParams;
import com.nexus.nexussync.params.MediatorClimate;
import com.nexus.nexussync.params.Params;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ContextBuilderTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

  /** Params with only the sections read by the context package; the rest is irrelevant here. */
  static Params params(MediatorClimate climate) {
    return new Params(
        "test",
        1L,
        Path.of("catalog"),
        Path.of("places.csv"),
        null,
        null,
        new AgentsParams("haiku", 90, "haiku", 120, climate),
        null,
        null,
        null,
        new ContextParams(7, 90),
        null,
        null,
        null);
  }

  static Constraints window(DayOfWeek day, int startHour, int startMinute, int endHour) {
    return new Constraints(
        "MID", "MID", day, LocalTime.of(startHour, startMinute), LocalTime.of(endHour, 0));
  }

  static Profile profile(
      int id, Constraints constraints, List<EmotionEntry> emotions, List<HistoryEntry> history) {
    return new Profile(
        id,
        "Coyoacán",
        Optional.empty(),
        Map.of("leer", 4),
        emotions,
        history,
        constraints,
        Optional.empty());
  }

  static HistoryEntry rated(String id, int daysAgo, int rating) {
    return new HistoryEntry(id, TODAY.minusDays(daysAgo), true, true, OptionalInt.of(rating));
  }

  // U3-06
  @Test
  void given_overlap_of_exactly_sixty_minutes_when_overlap_then_one_window() {
    List<Window> windows =
        Windows.overlap(
            window(DayOfWeek.SATURDAY, 10, 0, 14), window(DayOfWeek.SATURDAY, 13, 0, 18));

    assertThat(windows)
        .containsExactly(
            new Window(
                DayOfWeek.SATURDAY, LocalTime.of(13, 0), LocalTime.of(14, 0), Daypart.AFTERNOON));
  }

  // U3-06
  @Test
  void given_overlap_of_fifty_nine_minutes_when_overlap_then_no_window() {
    assertThat(
            Windows.overlap(
                window(DayOfWeek.SATURDAY, 10, 0, 14), window(DayOfWeek.SATURDAY, 13, 1, 18)))
        .isEmpty();
  }

  // U3-06
  @Test
  void given_different_days_or_disjoint_windows_when_overlap_then_no_window() {
    assertThat(
            Windows.overlap(
                window(DayOfWeek.SATURDAY, 10, 0, 14), window(DayOfWeek.SUNDAY, 10, 0, 14)))
        .isEmpty();
    assertThat(
            Windows.overlap(
                window(DayOfWeek.SUNDAY, 8, 0, 10), window(DayOfWeek.SUNDAY, 15, 0, 18)))
        .isEmpty();
  }

  // U3-07
  @Test
  void given_start_hours_when_daypart_of_then_boundaries_at_12_and_19() {
    assertThat(Windows.daypartOf(LocalTime.of(11, 59))).isEqualTo(Daypart.MORNING);
    assertThat(Windows.daypartOf(LocalTime.of(12, 0))).isEqualTo(Daypart.AFTERNOON);
    assertThat(Windows.daypartOf(LocalTime.of(18, 59))).isEqualTo(Daypart.AFTERNOON);
    assertThat(Windows.daypartOf(LocalTime.of(19, 0))).isEqualTo(Daypart.NIGHT);
  }

  // U3-08
  @Test
  void given_ids_in_any_order_when_couple_id_then_min_dash_max() {
    assertThat(ContextBuilder.coupleId(8, 4)).isEqualTo("4-8");
    assertThat(ContextBuilder.coupleId(4, 8)).isEqualTo("4-8");
  }

  // U3-09
  @Test
  void given_histories_when_rated_dates_then_distinct_chosen_rated_inside_window() {
    List<HistoryEntry> historyA =
        List.of(
            rated("cine", 5, 4),
            rated("museo", 10, 5),
            rated("viejo", 91, 5),
            new HistoryEntry("parque", TODAY.minusDays(3), true, false, OptionalInt.empty()),
            new HistoryEntry("bar", TODAY.minusDays(3), true, true, OptionalInt.empty()));
    List<HistoryEntry> historyB = List.of(rated("cine", 5, 2), rated("teatro", 90, 3));
    Profile a = profile(1, window(DayOfWeek.SATURDAY, 10, 0, 14), List.of(), historyA);
    Profile b = profile(2, window(DayOfWeek.SATURDAY, 10, 0, 14), List.of(), historyB);

    assertThat(ContextBuilder.ratedDates(a, b, TODAY, 90)).isEqualTo(3);
  }

  // U3-06, U3-08, U3-09
  @Test
  void given_two_profiles_when_build_then_context_is_complete() {
    Profile a =
        profile(
            8,
            window(DayOfWeek.SUNDAY, 9, 0, 13),
            List.of(new EmotionEntry(TODAY, "HAPPY", 5)),
            List.of(rated("cine", 2, 4)));
    Profile b = profile(4, window(DayOfWeek.SUNDAY, 10, 30, 15), List.of(), List.of());

    Context ctx =
        ContextBuilder.build(
            a,
            b,
            TODAY,
            params(MediatorClimate.AGGREGATED),
            Map.of(0, Weather.RAINY, 5, Weather.SUNNY));

    assertThat(ctx.coupleId()).isEqualTo("4-8");
    assertThat(ctx.climateA()).isEqualTo(new Climate(Level.HIGH, Level.HIGH));
    assertThat(ctx.climateB()).isEqualTo(new Climate(Level.UNKNOWN, Level.UNKNOWN));
    assertThat(ctx.windows())
        .containsExactly(
            new Window(
                DayOfWeek.SUNDAY, LocalTime.of(10, 30), LocalTime.of(13, 0), Daypart.MORNING));
    assertThat(ctx.weather()).containsExactly(Map.entry(0, Weather.RAINY));
    assertThat(ctx.ratedDatesCount()).isEqualTo(1);
    assertThat(ctx.today()).isEqualTo(TODAY);
  }

  // U3-06
  @Test
  void given_no_weather_when_build_then_every_window_is_unknown() {
    Profile a = profile(1, window(DayOfWeek.SATURDAY, 18, 0, 23), List.of(), List.of());
    Profile b = profile(2, window(DayOfWeek.SATURDAY, 19, 0, 22), List.of(), List.of());

    Context ctx = ContextBuilder.build(a, b, TODAY, params(MediatorClimate.NONE), Map.of());

    assertThat(ctx.windows()).extracting(Window::daypart).containsExactly(Daypart.NIGHT);
    assertThat(ctx.weather()).containsExactly(Map.entry(0, Weather.UNKNOWN));
  }

  // U3-06
  @Test
  void given_no_overlap_when_build_then_no_windows_and_no_weather() {
    Profile a = profile(1, window(DayOfWeek.SATURDAY, 8, 0, 9), List.of(), List.of());
    Profile b = profile(2, window(DayOfWeek.SATURDAY, 18, 0, 22), List.of(), List.of());

    Context ctx =
        ContextBuilder.build(a, b, TODAY, params(MediatorClimate.NONE), Map.of(0, Weather.SUNNY));

    assertThat(ctx.windows()).isEmpty();
    assertThat(ctx.weather()).isEmpty();
  }
}
