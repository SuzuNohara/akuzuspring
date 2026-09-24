package com.nexus.nexussync.sampler;

import static com.nexus.nexussync.sampler.SamplerFixtures.CENTRO;
import static com.nexus.nexussync.sampler.SamplerFixtures.TODAY;
import static com.nexus.nexussync.sampler.SamplerFixtures.act;
import static com.nexus.nexussync.sampler.SamplerFixtures.catalog;
import static com.nexus.nexussync.sampler.SamplerFixtures.ctx;
import static com.nexus.nexussync.sampler.SamplerFixtures.offered;
import static com.nexus.nexussync.sampler.SamplerFixtures.params;
import static com.nexus.nexussync.sampler.SamplerFixtures.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.Location;
import com.nexus.nexussync.params.ExplorationMethod;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.FilterName;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Deterministic sampler: selection, relaxation and terminal states (T-29, T-30). */
final class SamplerTest {

  private static final long SEED = 42L;

  /** Score = PRICE only, so the cost fixes the ranking under a PREMIUM budget. */
  private static Map<Feature, Double> priceOnly() {
    Map<Feature, Double> w = new EnumMap<>(Feature.class);
    w.put(Feature.PRICE, 1.0);
    return w;
  }

  private static SamplerFixtures.Params greedy(int size, int min) {
    SamplerFixtures.Params p = params();
    p.size = size;
    p.min = min;
    p.exploration = ExplorationMethod.NONE;
    p.homeShare = 0.0;
    p.maxPerType = 100;
    return p;
  }

  private static List<Activity> distinct(int n, boolean city) {
    List<Activity> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      SamplerFixtures.Act a = act(String.format("act-%02d", i)).type("T" + i).cost(i * 100);
      out.add((city ? a.city() : a).build());
    }
    return out;
  }

  private static Sample run(Context c, Catalog cat, SamplerFixtures.Params p, long seed) {
    return Sampler.sample(c, cat, priceOnly(), p.build(), new Random(seed));
  }

  private static List<String> ids(Sample s) {
    return s.items().stream().map(SampleItem::activityId).toList();
  }

  // U4-09
  @Test
  void given_sameSeed_when_sample_then_identicalSample() {
    Catalog cat = catalog(distinct(30, false), List.of(), Map.of());
    SamplerFixtures.Params p = greedy(10, 5);
    p.exploration = ExplorationMethod.EPS_GREEDY;
    p.eps0 = 0.5;

    Sample first = run(ctx().build(), cat, p, SEED);
    Sample second = run(ctx().build(), cat, p, SEED);

    assertThat(second).isEqualTo(first);
    assertThat(run(ctx().build(), cat, p, SEED + 1).items()).isNotEqualTo(first.items());
  }

  // U4-09
  @Test
  void given_noExploration_when_sample_then_topScoresInOrder() {
    Catalog cat = catalog(distinct(8, false), List.of(), Map.of());

    Sample s = run(ctx().build(), cat, greedy(3, 1), SEED);

    assertThat(ids(s)).containsExactly("act-00", "act-01", "act-02");
    assertThat(s.items()).noneMatch(SampleItem::explored);
    assertThat(s.items().get(0).score()).isCloseTo(1.0, within(1e-9));
    assertThat(s.status()).isEqualTo(SampleStatus.OK);
    assertThat(s.relaxations()).isEmpty();
  }

  // U4-10
  @Test
  void given_epsGreedy_when_sample_then_roundedShareExploredWithDecay() {
    final Catalog cat = catalog(distinct(30, false), List.of(), Map.of());
    SamplerFixtures.Params p = greedy(10, 5);
    p.exploration = ExplorationMethod.EPS_GREEDY;
    p.eps0 = 0.5;
    p.tau = 5.0;

    Sample fresh = run(ctx().build(), cat, p, SEED);
    Sample learned = run(ctx().rated(5).build(), cat, p, SEED);

    assertThat(fresh.items()).hasSize(10).filteredOn(SampleItem::explored).hasSize(5);
    assertThat(learned.items()).hasSize(10).filteredOn(SampleItem::explored).hasSize(3);
  }

  @Test
  void given_explorationParams_when_eps_then_decayOrZero() {
    SamplerFixtures.Params p = params();
    p.eps0 = 0.4;
    p.tau = 0.0;
    assertThat(Sampler.eps(p.build(), 10)).isCloseTo(0.4, within(1e-9));
    p.tau = 5.0;
    assertThat(Sampler.eps(p.build(), 5)).isCloseTo(0.2, within(1e-9));
    p.exploration = ExplorationMethod.NONE;
    assertThat(Sampler.eps(p.build(), 5)).isEqualTo(0.0);
  }

  // U4-11
  @Test
  void given_manyOfOneType_when_sample_then_maxPerTypeRespected() {
    List<Activity> acts = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      acts.add(act("x" + i).type("X").cost(i).build());
    }
    acts.add(act("y0").type("Y").cost(500).build());
    acts.add(act("y1").type("Y").cost(600).build());
    SamplerFixtures.Params p = greedy(4, 1);
    p.maxPerType = 2;

    Sample s = run(ctx().build(), catalog(acts, List.of(), Map.of()), p, SEED);

    assertThat(ids(s)).containsExactly("x0", "x1", "y0", "y1");
  }

  // U4-11
  @Test
  void given_notEnoughTypes_when_sample_then_secondPassFillsBeyondCap() {
    List<Activity> acts = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      acts.add(act("x" + i).type("X").cost(i).build());
    }
    acts.add(act("y0").type("Y").cost(500).build());
    SamplerFixtures.Params p = greedy(4, 1);
    p.maxPerType = 2;

    Sample s = run(ctx().build(), catalog(acts, List.of(), Map.of()), p, SEED);

    assertThat(ids(s)).containsExactly("x0", "x1", "x2", "y0");
  }

  // U4-12
  @Test
  void given_homeShare_when_sample_then_homeReservedDespiteLowerScore() {
    List<Activity> acts = new ArrayList<>(distinct(4, true));
    acts.add(act("home-a").type("H1").cost(5000).build());
    acts.add(act("home-b").type("H2").cost(6000).build());
    acts.add(act("home-c").type("H3").cost(7000).build());
    SamplerFixtures.Params p = greedy(4, 1);
    p.homeShare = 0.5;

    Sample s = run(ctx().build(), catalog(acts, List.of(), Map.of()), p, SEED);

    assertThat(ids(s)).containsExactly("act-00", "act-01", "home-a", "home-b");
  }

  // U4-12
  @Test
  void given_exploredHomes_when_sample_then_reserveCountsThem() {
    final List<Activity> acts = new ArrayList<>(distinct(6, false));
    SamplerFixtures.Params p = greedy(4, 1);
    p.homeShare = 1.0;
    p.exploration = ExplorationMethod.EPS_GREEDY;
    p.eps0 = 0.5;

    Sample s = run(ctx().build(), catalog(acts, List.of(), Map.of()), p, SEED);

    assertThat(s.items()).hasSize(4).filteredOn(SampleItem::explored).hasSize(2);
  }

  // U4-13
  @Test
  void given_everythingInCooldown_when_sample_then_cooldownRelaxedFirst() {
    SamplerFixtures.Ctx c = ctx();
    List<Activity> acts = distinct(5, false);
    acts.forEach(a -> c.historyA(offered(a.activityId(), TODAY.minusDays(1))));

    Sample s = run(c.build(), catalog(acts, List.of(), Map.of()), greedy(5, 3), SEED);

    assertThat(s.relaxations()).containsExactly(FilterName.COOLDOWN);
    assertThat(s.status()).isEqualTo(SampleStatus.OK);
    assertThat(s.items()).hasSize(5);
    assertThat(s.rejected().get(FilterName.COOLDOWN)).isZero();
  }

  // U4-13
  @Test
  void given_budgetTooLow_when_sample_then_relaxedInOrderUpToBudget() {
    List<Activity> acts = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      acts.add(act("a" + i).type("T" + i).cost(500).build());
    }
    Context c = ctx().budgets("LOW", "HIGH").build();

    Sample s = run(c, catalog(acts, List.of(), Map.of()), greedy(4, 3), SEED);

    assertThat(s.relaxations())
        .containsExactly(FilterName.COOLDOWN, FilterName.WEATHER, FilterName.BUDGET);
    assertThat(s.status()).isEqualTo(SampleStatus.OK);
  }

  // U4-13
  @Test
  void given_placesJustOutsideRadius_when_sample_then_radiusGrowsFiveKm() {
    Location near18km = new Location(CENTRO.lat() + 0.162, CENTRO.lon());
    List<Activity> acts = distinct(3, true);
    Map<String, List<String>> links = new java.util.HashMap<>();
    acts.forEach(a -> links.put(a.activityId(), List.of("p")));
    Catalog cat = catalog(acts, List.of(place("p", near18km)), links);
    Context c = ctx().locations(Optional.of(CENTRO), Optional.empty()).build();

    Sample s = run(c, cat, greedy(3, 3), SEED);

    assertThat(s.relaxations()).endsWith(FilterName.RADIUS).hasSize(4);
    assertThat(s.items()).hasSize(3);
    assertThat(s.status()).isEqualTo(SampleStatus.OK);
  }

  // U4-14
  @Test
  void given_tooFewAfterMaxRelaxations_when_sample_then_insufficientWithRejections() {
    List<Activity> acts = new ArrayList<>(distinct(2, false));
    acts.add(act("long-a").duration(999).build());
    acts.add(act("long-b").duration(999).build());
    acts.add(act("pricey").cost(99999).build());
    SamplerFixtures.Params p = greedy(5, 4);
    p.maxRelax = 2;

    Sample s = run(ctx().build(), catalog(acts, List.of(), Map.of()), p, SEED);

    assertThat(s.status()).isEqualTo(SampleStatus.INSUFFICIENT_SAMPLE);
    assertThat(s.relaxations()).containsExactly(FilterName.COOLDOWN, FilterName.WEATHER);
    assertThat(s.items()).hasSize(2);
    assertThat(s.rejected())
        .containsEntry(FilterName.DURATION, 2)
        .containsEntry(FilterName.BUDGET, 1)
        .containsEntry(FilterName.SCOPE, 0)
        .hasSize(FilterName.values().length);
  }

  // U4-14
  @Test
  void given_emptyRelaxOrder_when_sample_then_noRelaxation() {
    SamplerFixtures.Params p = greedy(5, 4);
    p.relax = List.of();

    Sample s = run(ctx().build(), catalog(distinct(2, false), List.of(), Map.of()), p, SEED);

    assertThat(s.relaxations()).isEmpty();
    assertThat(s.status()).isEqualTo(SampleStatus.INSUFFICIENT_SAMPLE);
  }

  // U4-15
  @Test
  void given_noWindows_when_sample_then_noWindowAndEmpty() {
    Sample s =
        run(
            ctx().windows().build(),
            catalog(distinct(5, false), List.of(), Map.of()),
            greedy(5, 1),
            SEED);

    assertThat(s.status()).isEqualTo(SampleStatus.NO_WINDOW);
    assertThat(s.items()).isEmpty();
    assertThat(s.relaxations()).isEmpty();
    assertThat(s.rejected()).isEmpty();
  }
}
