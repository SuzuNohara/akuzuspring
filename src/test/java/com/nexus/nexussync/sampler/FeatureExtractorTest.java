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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.context.Climate;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.Level;
import com.nexus.nexussync.context.Location;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.SamplerParams;
import com.nexus.nexussync.places.Geo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Feature vector of the sampler (T-26, T-27). */
final class FeatureExtractorTest {

  private static final double EPS = 1e-9;

  private static final SamplerParams P = params().build();

  private static final Climate UNKNOWN = new Climate(Level.UNKNOWN, Level.UNKNOWN);

  @Test
  void given_activity_when_of_then_everyFeatureInUnitRange() {
    Activity a = act("a").interests("cine").cost(100).ambience("QUIET").build();
    Map<Feature, Double> x = FeatureExtractor.of(a, ctx().build(), catalog(a), P);

    assertThat(x).containsOnlyKeys(Feature.values());
    assertThat(x.values()).allSatisfy(v -> assertThat(v).isBetween(0.0, 1.0));
  }

  // U4-02
  @Test
  void given_levelsOfBoth_when_interest_then_meanOfNormalisedLevels() {
    Activity a = act("a").interests("cine", "museos", "nadie").build();
    Context c = ctx().prefs(Map.of("cine", 5, "museos", 3), Map.of("cine", 4, "museos", 2)).build();
    double cine = (1.0 + 0.75) / 2;
    double museos = (0.5 + 0.25) / 2;

    assertThat(FeatureExtractor.interest(a, c.a(), c.b()))
        .isCloseTo((cine + museos) / 2, within(EPS));
  }

  // U4-02
  @Test
  void given_someLevelOne_when_interest_then_halved() {
    Activity a = act("a").interests("cine").build();
    Context c = ctx().prefs(Map.of("cine", 5), Map.of("cine", 1)).build();

    assertThat(FeatureExtractor.interest(a, c.a(), c.b())).isCloseTo(0.25, within(EPS));
  }

  // U4-02
  @Test
  void given_noInterestKnown_when_interest_then_zero() {
    Activity a = act("a").interests("cine").build();
    Context c = ctx().prefs(Map.of("museos", 5), Map.of()).build();

    assertThat(FeatureExtractor.interest(a, c.a(), c.b())).isEqualTo(0.0);
  }

  @Test
  void given_levelMissingForOnePerson_when_interest_then_neutralLevel() {
    Activity a = act("a").interests("cine").build();
    Context c = ctx().prefs(Map.of(), Map.of("cine", 5)).build();

    assertThat(FeatureExtractor.interest(a, c.a(), c.b())).isCloseTo(0.75, within(EPS));
    Context d = ctx().prefs(Map.of("cine", 5), Map.of()).build();
    assertThat(FeatureExtractor.interest(a, d.a(), d.b())).isCloseTo(0.75, within(EPS));
  }

  // U4-03
  @Test
  void given_costAndLowestBand_when_price_then_oneMinusShareOfCeiling() {
    Context c = ctx().budgets("HIGH", "MID").build();

    assertThat(FeatureExtractor.price(act("a").cost(200).build(), c)).isCloseTo(0.75, within(EPS));
    assertThat(FeatureExtractor.price(act("a").cost(5000).build(), c)).isEqualTo(0.0);
  }

  // U4-03
  @Test
  void given_zeroCeiling_when_price_then_oneOnlyForFree() {
    Context c = ctx().budgets("FREE", "PREMIUM").build();

    assertThat(FeatureExtractor.price(act("a").cost(0).build(), c)).isEqualTo(1.0);
    assertThat(FeatureExtractor.price(act("a").cost(1).build(), c)).isEqualTo(0.0);
  }

  // U4-04
  @Test
  void given_placeAtHalfRadius_when_distance_then_half() {
    Location half = new Location(CENTRO.lat() + 0.0674, CENTRO.lon());
    double d = Geo.distanceKm(CENTRO, half);
    Activity a = act("a").city().build();
    Catalog cat = catalog(List.of(a), List.of(place("p", half)), Map.of("a", List.of("p")));
    Context c = ctx().locations(Optional.of(CENTRO), Optional.empty()).build();

    assertThat(FeatureExtractor.distance(a, c, cat, 15.0)).isCloseTo(1 - d / 15.0, within(EPS));
    assertThat(FeatureExtractor.distance(a, c, cat, 1.0)).isEqualTo(0.0);
  }

  // U4-04
  @Test
  void given_noLocation_when_distance_then_neutral() {
    Activity a = act("a").city().build();
    assertThat(FeatureExtractor.distance(a, ctx().build(), catalog(a), 15.0)).isEqualTo(0.5);
  }

  // U4-04
  @Test
  void given_homeOrNoReachablePlace_when_distance_then_bounds() {
    Context c = ctx().locations(Optional.of(CENTRO), Optional.of(CENTRO)).build();
    Activity home = act("h").build();
    Activity city = act("c").city().build();

    assertThat(FeatureExtractor.distance(home, c, catalog(home), 15.0)).isEqualTo(1.0);
    assertThat(FeatureExtractor.distance(city, c, catalog(city), 15.0)).isEqualTo(0.0);
    Catalog cat = catalog(List.of(city), List.of(place("p", CENTRO)), Map.of("c", List.of("p")));
    assertThat(FeatureExtractor.distance(city, c, cat, 0.0)).isEqualTo(0.0);
  }

  // U4-05
  @Test
  void given_history_when_novelty_then_neverOfferedChosen() {
    Activity a = act("a").build();
    Context never = ctx().historyA(offered("b", TODAY)).build();
    Context offer = ctx().historyB(offered("a", TODAY.minusDays(60))).build();
    Context chosenOnce = ctx().historyA(chosen("a", TODAY.minusDays(60), 2)).build();

    assertThat(FeatureExtractor.novelty(a, never)).isEqualTo(1.0);
    assertThat(FeatureExtractor.novelty(a, offer)).isEqualTo(0.3);
    assertThat(FeatureExtractor.novelty(a, chosenOnce)).isEqualTo(0.0);
  }

  // U4-06
  @Test
  void given_climates_when_emotion_then_shareOfPreferredAmbienceTimesWeight() {
    Activity a = act("a").ambience("QUIET", "SOCIAL").build();
    final Context low = ctx().climates(new Climate(Level.LOW, Level.LOW), UNKNOWN).build();
    Context mixed =
        ctx()
            .climates(new Climate(Level.LOW, Level.LOW), new Climate(Level.HIGH, Level.MID))
            .build();
    SamplerFixtures.Params raw = params();
    raw.emotionWeight = 0.5;

    assertThat(FeatureExtractor.emotion(a, mixed, P)).isEqualTo(1.0);
    assertThat(FeatureExtractor.emotion(a, mixed, raw.build())).isEqualTo(0.5);
    assertThat(FeatureExtractor.emotion(a, low, P)).isEqualTo(1.0);
    Context lowBoth =
        ctx()
            .climates(new Climate(Level.LOW, Level.LOW), new Climate(Level.LOW, Level.MID))
            .build();
    assertThat(FeatureExtractor.emotion(a, lowBoth, P)).isEqualTo(0.5);
  }

  // U4-06
  @Test
  void given_disabledOrBothUnknownOrNoAmbience_when_emotion_then_neutral() {
    Activity a = act("a").ambience("ACTIVE").build();
    Context known = ctx().climates(new Climate(Level.LOW, Level.LOW), UNKNOWN).build();
    SamplerFixtures.Params raw = params();
    raw.emotion = false;

    assertThat(FeatureExtractor.emotion(a, known, raw.build())).isEqualTo(0.5);
    assertThat(FeatureExtractor.emotion(a, ctx().build(), P)).isEqualTo(0.5);
    assertThat(FeatureExtractor.emotion(act("b").build(), known, P)).isEqualTo(0.5);
    Context partly =
        ctx()
            .climates(new Climate(Level.LOW, Level.UNKNOWN), new Climate(Level.UNKNOWN, Level.HIGH))
            .build();
    assertThat(FeatureExtractor.emotion(a, partly, P)).isEqualTo(0.5);
  }

  // U4-07
  @Test
  void given_chosenHistory_when_collab_then_closenessToMeanChosen() {
    Activity c2 = act("c2").collab(2).build();
    Activity c8 = act("c8").collab(8).build();
    Activity target = act("t").collab(9).build();
    Catalog cat = catalog(c2, c8, target);
    Context c =
        ctx()
            .historyA(chosen("c2", TODAY, 5))
            .historyB(chosen("c8", TODAY, 5))
            .historyB(chosen("gone", TODAY, 5))
            .historyB(offered("c2", TODAY))
            .build();

    assertThat(FeatureExtractor.collab(target, c, cat)).isCloseTo(0.6, within(EPS));
  }

  // U4-07
  @Test
  void given_noChosen_when_collab_then_targetFive() {
    Activity a = act("a").collab(10).build();
    assertThat(FeatureExtractor.collab(a, ctx().build(), catalog(a))).isCloseTo(0.5, within(EPS));
  }

  // U4-07
  @Test
  void given_seasons_when_season_then_exactAnyOrNone() {
    Context c = ctx().build();

    assertThat(FeatureExtractor.season(act("a").seasons(Seasons.RAINY, Seasons.ANY).build(), c))
        .isEqualTo(1.0);
    assertThat(FeatureExtractor.season(act("a").seasons(Seasons.ANY).build(), c)).isEqualTo(0.5);
    assertThat(FeatureExtractor.season(act("a").seasons(Seasons.WINTER).build(), c)).isEqualTo(0.0);
  }
}
