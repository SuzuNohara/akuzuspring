package com.nexus.nexussync.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.LocationScope;
import com.nexus.nexussync.context.Climate;
import com.nexus.nexussync.context.Constraints;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.Level;
import com.nexus.nexussync.context.Profile;
import com.nexus.nexussync.params.ExplorationMethod;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.LearningMethod;
import com.nexus.nexussync.params.SamplerParams;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TruthRankerTest {

  private static final SamplerParams SAMPLER =
      new SamplerParams(
          40,
          15,
          15.0,
          30,
          2,
          0.25,
          0.6,
          Map.of(Feature.COLLAB, 1.0),
          LearningMethod.NONE,
          0.3,
          ExplorationMethod.NONE,
          0.0,
          5.0,
          false,
          1.0,
          List.of(),
          0);

  // COLLAB = 1 - |collaboration - 5| / 10 without history: c5 1.0, b7 0.8, a3 0.8, d0 0.5.
  private static final Catalog CATALOG =
      catalog(activity("c5", 5), activity("b7", 7), activity("a3", 3), activity("d0", 0));

  private static final List<String> FINAL = List.of("d0", "b7", "a3", "c5");

  private static Activity activity(String id, int collaboration) {
    return new Activity(
        id,
        id,
        "GAME",
        LocationScope.HOME,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of("ANY"),
        60,
        90,
        120,
        1,
        1,
        collaboration,
        1,
        0,
        "FREE",
        false,
        Set.of(),
        Set.of(),
        "");
  }

  private static Catalog catalog(Activity... activities) {
    Map<String, Activity> byId =
        Stream.of(activities).collect(Collectors.toMap(Activity::activityId, Function.identity()));
    return new Catalog(byId, Map.of(), Map.of());
  }

  private static Profile profile(Optional<Map<Feature, Double>> truth) {
    Constraints c =
        new Constraints("MID", "LOW", DayOfWeek.SATURDAY, LocalTime.of(10, 0), LocalTime.of(14, 0));
    return new Profile(1, "Coyoacan", Optional.empty(), Map.of(), List.of(), List.of(), c, truth);
  }

  private static Context context(Profile p) {
    Climate unknown = new Climate(Level.UNKNOWN, Level.UNKNOWN);
    return new Context(
        "1-1", p, p, unknown, unknown, List.of(), Map.of(), 0, LocalDate.of(2026, 9, 27));
  }

  private static Profile truthful() {
    return profile(Optional.of(Map.of(Feature.COLLAB, 1.0)));
  }

  // U8-07
  @Test
  void givenNoNoise_whenRank_thenTopThreeByTruthScoreWithLexicographicTieBreak()
      throws DecisionException {
    Profile p = truthful();

    List<String> first =
        TruthRanker.rank(FINAL, p, CATALOG, context(p), SAMPLER, 0.0, new Random(1));
    List<String> second =
        TruthRanker.rank(FINAL, p, CATALOG, context(p), SAMPLER, 0.0, new Random(99));

    assertThat(first).containsExactly("c5", "a3", "b7");
    assertThat(second).isEqualTo(first);
  }

  @Test
  void givenNoNoise_whenRank_thenRandomNotConsumed() throws DecisionException {
    Profile p = truthful();
    Random rng = new Random(5);

    TruthRanker.rank(FINAL, p, CATALOG, context(p), SAMPLER, 0.0, rng);

    assertThat(rng.nextLong()).isEqualTo(new Random(5).nextLong());
  }

  // U8-08
  @Test
  void givenUnitNoise_whenRankWithTwentySeeds_thenAtLeastTwoOrders() throws DecisionException {
    Profile p = truthful();
    Set<List<String>> orders = new HashSet<>();

    for (long seed = 0; seed < 20; seed++) {
      orders.add(TruthRanker.rank(FINAL, p, CATALOG, context(p), SAMPLER, 1.0, new Random(seed)));
    }

    assertThat(orders).hasSizeGreaterThanOrEqualTo(2);
  }

  // U8-08
  @Test
  void givenProfileWithoutTruthWeights_whenRank_thenDecisionException() {
    Profile p = profile(Optional.empty());

    assertThatThrownBy(
            () -> TruthRanker.rank(FINAL, p, CATALOG, context(p), SAMPLER, 0.0, new Random(1)))
        .isInstanceOf(DecisionException.class)
        .hasMessageContaining("truthWeights");
  }

  @Test
  void givenNegativeNoise_whenRank_thenDecisionException() {
    Profile p = truthful();

    assertThatThrownBy(
            () -> TruthRanker.rank(FINAL, p, CATALOG, context(p), SAMPLER, -0.1, new Random(1)))
        .isInstanceOf(DecisionException.class);
  }

  @Test
  void givenIdOutsideCatalog_whenRank_thenDecisionException() {
    Profile p = truthful();

    assertThatThrownBy(
            () ->
                TruthRanker.rank(
                    List.of("c5", "zz"), p, CATALOG, context(p), SAMPLER, 0.0, new Random(1)))
        .isInstanceOf(DecisionException.class)
        .hasMessageContaining("zz");
  }

  @Test
  void givenTwoFinalIds_whenRank_thenBothRanked() throws DecisionException {
    Profile p = truthful();

    List<String> ranked =
        TruthRanker.rank(List.of("d0", "c5"), p, CATALOG, context(p), SAMPLER, 0.0, new Random(1));

    assertThat(ranked).containsExactly("c5", "d0");
  }
}
