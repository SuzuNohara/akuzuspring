package com.nexus.nexussync.sampler;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.LocationScope;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.params.ExplorationMethod;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.FilterName;
import com.nexus.nexussync.params.SamplerParams;
import com.nexus.nexussync.sampler.Budget.PriceBand;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Deterministic sampler of G2 (§3.4): hard filters, features, score, exploration, diversity and
 * relaxation.
 *
 * <p>Candidates are processed in ascending id order before the {@link Random} is used (X4), so a
 * given seed always yields the same sample. {@code java.util.Random} is used on purpose: this is a
 * reproducible simulation, not cryptography.
 */
public final class Sampler {

  private static final Comparator<SampleItem> BY_SCORE =
      Comparator.comparingDouble(SampleItem::score)
          .reversed()
          .thenComparing(SampleItem::activityId);

  private Sampler() {}

  /**
   * Builds the sample of a couple.
   *
   * <p>Without shared windows returns {@link SampleStatus#NO_WINDOW}. Otherwise filters, scores and
   * selects; while the sample is smaller than {@code s_min} it relaxes the filters in {@code
   * relax_order}, at most {@code max_relaxations} steps, and ends in {@link
   * SampleStatus#INSUFFICIENT_SAMPLE} if that is still not enough. The exploration rate is {@code
   * eps0 / (1 + ratedDatesCount / tau)}.
   *
   * @param ctx context of the couple
   * @param cat catalog
   * @param w weight per feature
   * @param p sampler parameters
   * @param rng seeded random source of the run
   * @return the sample, never {@code null}
   * @implNote O(R * n * (f + h + l)) time, R = relaxation steps + 1, n = activities; O(n) space.
   */
  public static Sample sample(
      Context ctx, Catalog cat, Map<Feature, Double> w, SamplerParams p, Random rng) {
    if (ctx.windows().isEmpty()) {
      return new Sample(List.of(), List.of(), SampleStatus.NO_WINDOW, Map.of());
    }
    State st =
        new State(
            p.radiusKm(),
            Budget.lowest(ctx.a().constraints().budgetBand(), ctx.b().constraints().budgetBand()),
            EnumSet.noneOf(FilterName.class));
    List<FilterName> applied = new ArrayList<>();
    double eps = eps(p, ctx.ratedDatesCount());
    Pass pass = pass(ctx, cat, w, p, st);
    List<SampleItem> items = select(pass.scored(), cat, p, eps, rng);
    int steps = Math.min(p.maxRelaxations(), p.relaxOrder().size());
    while (items.size() < p.sampleMin() && applied.size() < steps) {
      FilterName step = p.relaxOrder().get(applied.size());
      st = st.relax(step);
      applied.add(step);
      pass = pass(ctx, cat, w, p, st);
      items = select(pass.scored(), cat, p, eps, rng);
    }
    SampleStatus status =
        items.size() < p.sampleMin() ? SampleStatus.INSUFFICIENT_SAMPLE : SampleStatus.OK;
    return new Sample(items, applied, status, pass.rejected());
  }

  /**
   * Exploration rate {@code eps0 / (1 + choices / tau)}; 0 when exploration is off.
   *
   * @implNote O(1) time and space.
   */
  static double eps(SamplerParams p, int choices) {
    if (p.explorationMethod() == ExplorationMethod.NONE) {
      return 0.0;
    }
    double decay = p.tau() > 0.0 ? choices / p.tau() : 0.0;
    return p.eps0() / (1.0 + decay);
  }

  /**
   * Selects the sample from the scored eligible activities: exploration at random, HOME reserve,
   * best score under {@code max_per_type}, and a second pass without the type cap to fill up.
   *
   * @param scored eligible activities with their score, in ascending id order
   * @param cat catalog, for the type and scope of each activity
   * @param p sampler parameters
   * @param eps exploration rate
   * @param rng seeded random source
   * @return at most {@code n_sample} items, best score first
   * @implNote O(n log n) time, O(n) space.
   */
  static List<SampleItem> select(
      List<SampleItem> scored, Catalog cat, SamplerParams p, double eps, Random rng) {
    Picker picker = new Picker(cat, p.sampleSize(), p.maxPerType());
    List<SampleItem> shuffled = new ArrayList<>(scored);
    shuffled.sort(Comparator.comparing(SampleItem::activityId));
    Collections.shuffle(shuffled, rng);
    int explore = (int) Math.min(Math.round(eps * p.sampleSize()), p.sampleSize());
    picker.take(shuffled, explore, true, true);
    List<SampleItem> ranked = new ArrayList<>(scored);
    ranked.sort(BY_SCORE);
    int homeTarget = (int) Math.ceil(p.homeShareMin() * p.sampleSize());
    List<SampleItem> homes = ranked.stream().filter(picker::isHome).toList();
    picker.take(homes, homeTarget - picker.homes(), true, false);
    picker.take(ranked, p.sampleSize(), true, false);
    picker.take(ranked, p.sampleSize(), false, false);
    List<SampleItem> out = new ArrayList<>(picker.chosen());
    out.sort(BY_SCORE);
    return out;
  }

  private static Pass pass(
      Context ctx, Catalog cat, Map<Feature, Double> w, SamplerParams p, State st) {
    Map<FilterName, Integer> rejected = new EnumMap<>(FilterName.class);
    for (FilterName f : FilterName.values()) {
      rejected.put(f, 0);
    }
    List<SampleItem> scored = new ArrayList<>();
    for (String id : cat.activities().keySet().stream().sorted().toList()) {
      Activity a = cat.activities().get(id);
      Optional<FilterName> r = firstRejection(a, ctx, cat, p, st);
      if (r.isPresent()) {
        rejected.merge(r.get(), 1, Integer::sum);
      } else {
        Map<Feature, Double> x = FeatureExtractor.of(a, ctx, cat, p);
        scored.add(new SampleItem(id, Scorer.score(x, w), x, false));
      }
    }
    return new Pass(scored, rejected);
  }

  private static Optional<FilterName> firstRejection(
      Activity a, Context ctx, Catalog cat, SamplerParams p, State st) {
    List<HardFilter> filters = HardFilters.all();
    FilterName[] names = FilterName.values();
    for (int i = 0; i < filters.size(); i++) {
      if (st.disabled().contains(names[i])) {
        continue;
      }
      Optional<FilterName> r = filters.get(i).reject(a, ctx, cat, p, st.radiusKm(), st.budget());
      if (r.isPresent()) {
        return r;
      }
    }
    return Optional.empty();
  }

  /** Scored eligible activities and rejection count per filter of one pass. */
  private record Pass(List<SampleItem> scored, Map<FilterName, Integer> rejected) {}

  /** Relaxable state of the filters. */
  private record State(double radiusKm, PriceBand budget, Set<FilterName> disabled) {

    State relax(FilterName step) {
      Relaxer.Relaxation r = Relaxer.apply(step, radiusKm, budget);
      Set<FilterName> off = EnumSet.noneOf(FilterName.class);
      off.addAll(disabled);
      r.disabled().ifPresent(off::add);
      return new State(r.radiusKm(), r.budget(), off);
    }
  }

  /** Accumulates the chosen items under the size, type and HOME rules. */
  private static final class Picker {

    private final Catalog cat;
    private final int size;
    private final int maxPerType;
    private final Map<String, SampleItem> chosen = new HashMap<>();
    private final List<SampleItem> order = new ArrayList<>();
    private final Map<String, Integer> perType = new HashMap<>();
    private int homes;

    Picker(Catalog cat, int size, int maxPerType) {
      this.cat = cat;
      this.size = size;
      this.maxPerType = maxPerType;
    }

    void take(List<SampleItem> candidates, int limit, boolean capType, boolean explored) {
      int taken = 0;
      for (SampleItem it : candidates) {
        if (taken >= limit || order.size() >= size) {
          return;
        }
        if (chosen.containsKey(it.activityId()) || capType && full(it)) {
          continue;
        }
        add(explored ? new SampleItem(it.activityId(), it.score(), it.features(), true) : it);
        taken++;
      }
    }

    boolean isHome(SampleItem it) {
      return cat.activities().get(it.activityId()).locationScope() == LocationScope.HOME;
    }

    int homes() {
      return homes;
    }

    List<SampleItem> chosen() {
      return order;
    }

    private boolean full(SampleItem it) {
      return perType.getOrDefault(type(it), 0) >= maxPerType;
    }

    private void add(SampleItem it) {
      chosen.put(it.activityId(), it);
      order.add(it);
      perType.merge(type(it), 1, Integer::sum);
      homes += isHome(it) ? 1 : 0;
    }

    private String type(SampleItem it) {
      return cat.activities().get(it.activityId()).activityType();
    }
  }
}
