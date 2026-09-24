package com.nexus.nexussync.sampler;

import static com.nexus.nexussync.sampler.SamplerFixtures.CENTRO;
import static com.nexus.nexussync.sampler.SamplerFixtures.TODAY;
import static com.nexus.nexussync.sampler.SamplerFixtures.act;
import static com.nexus.nexussync.sampler.SamplerFixtures.catalog;
import static com.nexus.nexussync.sampler.SamplerFixtures.chosen;
import static com.nexus.nexussync.sampler.SamplerFixtures.ctx;
import static com.nexus.nexussync.sampler.SamplerFixtures.offered;
import static com.nexus.nexussync.sampler.SamplerFixtures.params;
import static com.nexus.nexussync.sampler.SamplerFixtures.place;
import static com.nexus.nexussync.sampler.SamplerFixtures.window;
import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.Daypart;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.Location;
import com.nexus.nexussync.context.Weather;
import com.nexus.nexussync.params.FilterName;
import com.nexus.nexussync.params.SamplerParams;
import com.nexus.nexussync.sampler.Budget.PriceBand;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The eight hard filters of G2 (T-24, T-25): one passing and one rejected case each. */
final class HardFiltersTest {

  /** About 11 km north of the Zócalo. */
  private static final Location NORTH = new Location(19.5326, -99.1332);

  /** About 33 km north of the Zócalo. */
  private static final Location FAR = new Location(19.7326, -99.1332);

  private static final SamplerParams P = params().build();

  private static Optional<FilterName> run(HardFilter f, Activity a, Context c, Catalog cat) {
    return run(f, a, c, cat, P);
  }

  private static Optional<FilterName> run(
      HardFilter f, Activity a, Context c, Catalog cat, SamplerParams p) {
    return f.reject(a, c, cat, p, p.radiusKm(), PriceBand.MID);
  }

  private static Optional<FilterName> run(HardFilter f, Activity a, Context c) {
    return run(f, a, c, catalog(a));
  }

  @Test
  void given_allFilters_when_listed_then_pipelineOrderAlignedWithNames() {
    Activity a = act("x").city().cost(99999).duration(9999).seasons().outdoor().build();
    Context c = ctx().windows().build();
    List<HardFilter> all = HardFilters.all();

    assertThat(all).hasSize(FilterName.values().length);
    assertThat(run(all.get(0), a, c)).contains(FilterName.SCOPE);
    assertThat(run(all.get(1), a, c)).contains(FilterName.BUDGET);
    assertThat(run(all.get(2), a, c)).contains(FilterName.DURATION);
    assertThat(run(all.get(4), a, c)).contains(FilterName.SEASON);
  }

  // U4-01 SCOPE
  @Test
  void given_homeWithoutWindows_when_scope_then_passes() {
    assertThat(run(HardFilters::scope, act("h").build(), ctx().windows().build())).isEmpty();
  }

  // U4-01 SCOPE
  @Test
  void given_cityWithoutWindows_when_scope_then_rejected() {
    assertThat(run(HardFilters::scope, act("c").city().build(), ctx().windows().build()))
        .contains(FilterName.SCOPE);
    assertThat(run(HardFilters::scope, act("c").city().build(), ctx().build())).isEmpty();
  }

  // U4-01 BUDGET
  @Test
  void given_costAtCeiling_when_budget_then_passes() {
    assertThat(run(HardFilters::budget, act("a").cost(800).build(), ctx().build())).isEmpty();
  }

  // U4-01 BUDGET
  @Test
  void given_costAboveCeiling_when_budget_then_rejected() {
    assertThat(run(HardFilters::budget, act("a").cost(801).build(), ctx().build()))
        .contains(FilterName.BUDGET);
  }

  // U4-01 DURATION
  @Test
  void given_durationFitsSomeWindow_when_duration_then_passes() {
    Context c =
        ctx().windows(window(LocalTime.of(9, 0), 60), window(LocalTime.of(15, 0), 180)).build();
    assertThat(run(HardFilters::duration, act("a").duration(180).build(), c)).isEmpty();
  }

  // U4-01 DURATION
  @Test
  void given_durationLongerThanEveryWindow_when_duration_then_rejected() {
    Context c = ctx().windows(window(LocalTime.of(9, 0), 60)).build();
    assertThat(run(HardFilters::duration, act("a").duration(61).build(), c))
        .contains(FilterName.DURATION);
  }

  // U4-01 DAYPART
  @Test
  void given_daypartMatchesOrEmpty_when_daypart_then_passes() {
    Context c = ctx().windows(window(LocalTime.of(20, 0), 120)).build();
    assertThat(run(HardFilters::daypart, act("a").dayparts(Daypart.NIGHT).build(), c)).isEmpty();
    assertThat(run(HardFilters::daypart, act("b").build(), c)).isEmpty();
  }

  // U4-01 DAYPART
  @Test
  void given_noSharedDaypart_when_daypart_then_rejected() {
    Context c = ctx().windows(window(LocalTime.of(20, 0), 120)).build();
    Activity a = act("a").dayparts(Daypart.MORNING, Daypart.AFTERNOON).build();
    assertThat(run(HardFilters::daypart, a, c)).contains(FilterName.DAYPART);
  }

  // U4-01 SEASON
  @Test
  void given_anyOrActiveSeason_when_season_then_passes() {
    Context c = ctx().build();
    assertThat(run(HardFilters::season, act("a").seasons(Seasons.ANY).build(), c)).isEmpty();
    assertThat(run(HardFilters::season, act("b").seasons(Seasons.RAINY).build(), c)).isEmpty();
  }

  // U4-01 SEASON
  @Test
  void given_inactiveSeason_when_season_then_rejected() {
    Context c = ctx().build();
    Activity a = act("a").seasons(Seasons.NAVIDAD, Seasons.WINTER).build();
    assertThat(run(HardFilters::season, a, c)).contains(FilterName.SEASON);
  }

  // U4-01 RADIUS
  @Test
  void given_linkedPlaceWithinRadiusOfMidpoint_when_radius_then_passes() {
    Activity a = act("a").city().build();
    Catalog cat = catalog(List.of(a), List.of(place("p", NORTH)), Map.of("a", List.of("p")));
    Context both = ctx().locations(Optional.of(CENTRO), Optional.of(FAR)).build();
    Context onlyB = ctx().locations(Optional.empty(), Optional.of(NORTH)).build();

    assertThat(run(HardFilters::radius, a, both, cat)).isEmpty();
    assertThat(run(HardFilters::radius, a, onlyB, cat)).isEmpty();
  }

  // U4-01 RADIUS
  @Test
  void given_placesOutsideRadiusOrNone_when_radius_then_rejected() {
    Activity a = act("a").city().build();
    Catalog cat =
        catalog(List.of(a), List.of(place("p", FAR)), Map.of("a", List.of("p", "missing")));
    Context onlyA = ctx().locations(Optional.of(CENTRO), Optional.empty()).build();

    assertThat(run(HardFilters::radius, a, onlyA, cat)).contains(FilterName.RADIUS);
    assertThat(run(HardFilters::radius, a, onlyA, catalog(a))).contains(FilterName.RADIUS);
    assertThat(HardFilters.radius(a, onlyA, cat, P, 40.0, PriceBand.MID)).isEmpty();
  }

  @Test
  void given_homeOrNoLocation_when_radius_then_passes() {
    Context withLoc = ctx().locations(Optional.of(CENTRO), Optional.empty()).build();
    assertThat(run(HardFilters::radius, act("h").build(), withLoc)).isEmpty();
    assertThat(run(HardFilters::radius, act("c").city().build(), ctx().build())).isEmpty();
  }

  // U4-01 WEATHER
  @Test
  void given_outdoorAndSomeWindowNotRainy_when_weather_then_passes() {
    Context c =
        ctx()
            .windows(window(LocalTime.of(9, 0), 120), window(LocalTime.of(15, 0), 120))
            .weather(0, Weather.RAINY)
            .weather(1, Weather.UNKNOWN)
            .build();
    assertThat(run(HardFilters::weather, act("a").outdoor().build(), c)).isEmpty();
    assertThat(run(HardFilters::weather, act("a").outdoor().build(), ctx().build())).isEmpty();
  }

  // U4-01 WEATHER
  @Test
  void given_outdoorAndEveryWindowRainy_when_weather_then_rejected() {
    Context c = ctx().weather(0, Weather.RAINY).build();
    assertThat(run(HardFilters::weather, act("a").outdoor().build(), c))
        .contains(FilterName.WEATHER);
  }

  @Test
  void given_indoorOrRainMaxOneOrNoWindows_when_weather_then_passes() {
    Context rainy = ctx().weather(0, Weather.RAINY).build();
    SamplerFixtures.Params raw = params();
    raw.rainMax = 1.0;
    SamplerParams anyRain = raw.build();
    assertThat(run(HardFilters::weather, act("a").build(), rainy)).isEmpty();
    assertThat(run(HardFilters::weather, act("a").outdoor().build(), rainy, catalog(), anyRain))
        .isEmpty();
    assertThat(run(HardFilters::weather, act("a").outdoor().build(), ctx().windows().build()))
        .isEmpty();
  }

  // U4-01 COOLDOWN
  @Test
  void given_recentOfferChosenAndLikedOrOld_when_cooldown_then_passes() {
    Context liked = ctx().historyA(chosen("a", TODAY.minusDays(3), 4)).build();
    Context old = ctx().historyB(offered("a", TODAY.minusDays(30))).build();
    Context future = ctx().historyB(offered("a", TODAY.plusDays(1))).build();
    Context other = ctx().historyB(offered("b", TODAY.minusDays(1))).build();

    assertThat(run(HardFilters::cooldown, act("a").build(), liked)).isEmpty();
    assertThat(run(HardFilters::cooldown, act("a").build(), old)).isEmpty();
    assertThat(run(HardFilters::cooldown, act("a").build(), future)).isEmpty();
    assertThat(run(HardFilters::cooldown, act("a").build(), other)).isEmpty();
  }

  // U4-01 COOLDOWN
  @Test
  void given_recentOfferNotLiked_when_cooldown_then_rejected() {
    LocalDate recent = TODAY.minusDays(29);
    Context offer = ctx().historyB(offered("a", recent)).build();
    Context lowRating = ctx().historyA(chosen("a", TODAY, 3)).build();

    assertThat(run(HardFilters::cooldown, act("a").build(), offer)).contains(FilterName.COOLDOWN);
    assertThat(run(HardFilters::cooldown, act("a").build(), lowRating))
        .contains(FilterName.COOLDOWN);
  }
}
