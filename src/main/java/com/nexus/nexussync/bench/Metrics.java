package com.nexus.nexussync.bench;

import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.rounds.Agent;
import com.nexus.nexussync.rounds.Closure;
import com.nexus.nexussync.rounds.Pick;
import com.nexus.nexussync.sampler.SampleItem;
import com.nexus.nexussync.sampler.Scorer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * Bench metrics over the runs of one experiment (unit U11, §3.11; formal definitions in chapter 8,
 * table 47).
 *
 * <p>Every metric is {@link Double#NaN} when it has no data (empty list, or no run carrying what
 * the metric measures). Definitions, R being the runs:
 *
 * <ul>
 *   <li>{@value #F1}, {@value #F2}, {@value #F3}, {@value #AI_UNAVAILABLE}: share of runs with that
 *       closure.
 *   <li>{@value #MEAN_INTERSECTION}: mean size of the round-one intersection ({@link
 *       RunRecord.Evidence#intersectionF1()}).
 *   <li>{@value #HALLUCINATION}: invented ids over returned ids (valid + invented), summed over
 *       every pick of rounds one and two.
 *   <li>{@value #TYPE_DIVERSITY}: mean of distinct {@code activity_type} over final size, over the
 *       runs with a non-empty final list.
 *   <li>{@value #MEAN_SECONDS}: mean elapsed seconds.
 *   <li>{@value #TRUTH_ALIGNMENT}: mean over the runs with learned weights of the mean cosine
 *       between those weights and the {@code truthWeights} of each person that has them.
 *   <li>{@value #CHOSEN_TOP3}: share of successful persona picks of round one whose first id is in
 *       the top 3 of the sample ordered by the score with that person's {@code truthWeights}; a
 *       successful pick without ids is left out of the denominator (D-29).
 *   <li>{@value #HOURS_PARSED}: proposed places with interpreted hours over proposed places with
 *       non-empty hours.
 * </ul>
 *
 * <p>Calibration metrics ({@link #of(List, Map)}, Fase C2, U13-02..04), R_g being the runs of
 * couples with a {@link GoldEntry}:
 *
 * <ul>
 *   <li>{@value #GOLD_HIT}: share of R_g whose final types meet {@code expected_types}.
 *   <li>{@value #GOLD_VIOLATION}: share of R_g whose final list holds a {@code forbidden_type} or a
 *       {@code forbidden_id}.
 *   <li>{@value #FAIRNESS_GAP}: chapter 8 §8.4, {@code (1/|R|) Σ_r |τ_A(r) − τ_B(r)|}, τ_X(r) ∈ [0,
 *       1] being the alignment of the outcome of r with X's {@code truthWeights}. The outcome is
 *       the decision (D-32): τ_X(r) is {@code Scorer.score} of the sample features of the chosen
 *       activity under X's {@code truthWeights}; a run without decision falls back to the mean of
 *       that score over its final list, so every run with a result counts, with or without learned
 *       weights. Only the runs where τ cannot be computed are left out: a person without {@code
 *       truthWeights}, or no outcome id present in the sample.
 *   <li>{@value #STABILITY}: per couple, mean Jaccard of the final lists of every pair of distinct
 *       seeds (runs paired by iteration order within each seed; two empty lists count as 1), then
 *       mean over the couples with at least two seeds.
 *   <li>{@value #CALLS_PER_RUN}: mean agent calls consumed per run ({@link
 *       RunRecord.Evidence#calls()}).
 * </ul>
 */
public final class Metrics {

  /** Share of F1 closures. */
  public static final String F1 = "closureF1Rate";

  /** Share of F2 closures. */
  public static final String F2 = "closureF2Rate";

  /** Share of F3 closures. */
  public static final String F3 = "closureF3Rate";

  /** Share of AI_UNAVAILABLE closures. */
  public static final String AI_UNAVAILABLE = "aiUnavailableRate";

  /** Mean size of the round-one intersection. */
  public static final String MEAN_INTERSECTION = "meanIntersectionF1";

  /** Invented ids over returned ids. */
  public static final String HALLUCINATION = "hallucinationRate";

  /** Mean distinct types over final size. */
  public static final String TYPE_DIVERSITY = "finalTypeDiversity";

  /** Mean elapsed seconds per run. */
  public static final String MEAN_SECONDS = "meanSeconds";

  /** Mean cosine between learned and truth weights. */
  public static final String TRUTH_ALIGNMENT = "truthAlignment";

  /** Share of persona picks whose first id is in the truth top 3 of the sample. */
  public static final String CHOSEN_TOP3 = "chosenInTop3TruthRate";

  /** Proposed places with interpreted hours over those with non-empty hours. */
  public static final String HOURS_PARSED = "hoursParsedRate";

  /** Share of gold runs whose final types meet the expected ones. */
  public static final String GOLD_HIT = "goldHitRate";

  /** Share of gold runs whose final list holds a forbidden type or id. */
  public static final String GOLD_VIOLATION = "goldViolationRate";

  /** Mean absolute difference between the alignments with A's and B's truth. */
  public static final String FAIRNESS_GAP = "fairnessGap";

  /** Mean Jaccard of the final lists between seeds. */
  public static final String STABILITY = "stabilityAtSeed";

  /** Mean agent calls consumed per run. */
  public static final String CALLS_PER_RUN = "callsPerRun";

  /** Calibration metrics added by {@link #of(List, Map)}, in report order. */
  public static final List<String> CALIBRATION_NAMES =
      List.of(GOLD_HIT, GOLD_VIOLATION, FAIRNESS_GAP, STABILITY, CALLS_PER_RUN);

  /** Every metric, in report order. */
  public static final List<String> NAMES =
      List.of(
          F1,
          F2,
          F3,
          AI_UNAVAILABLE,
          MEAN_INTERSECTION,
          HALLUCINATION,
          TYPE_DIVERSITY,
          MEAN_SECONDS,
          TRUTH_ALIGNMENT,
          CHOSEN_TOP3,
          HOURS_PARSED);

  private static final int TOP = 3;
  private static final double NANOS_PER_SECOND = 1e9;

  private Metrics() {}

  /**
   * Computes the eleven metrics.
   *
   * @param rs runs of one experiment
   * @return every metric of {@link #NAMES}, in that order; NaN where there is no data
   * @implNote O(r · (s log s + k)) time and O(s) space, r runs, s sample size, k picked ids.
   */
  public static Map<String, Double> of(List<RunRecord> rs) {
    Map<String, Double> out = new LinkedHashMap<>();
    out.put(F1, share(rs, r -> r.gate().closure() == Closure.F1 ? 1 : 0));
    out.put(F2, share(rs, r -> r.gate().closure() == Closure.F2 ? 1 : 0));
    out.put(F3, share(rs, r -> r.gate().closure() == Closure.F3 ? 1 : 0));
    out.put(AI_UNAVAILABLE, share(rs, r -> r.gate().closure() == Closure.AI_UNAVAILABLE ? 1 : 0));
    out.put(MEAN_INTERSECTION, share(rs, r -> r.evidence().intersectionF1()));
    out.put(HALLUCINATION, hallucination(rs));
    out.put(TYPE_DIVERSITY, typeDiversity(rs));
    out.put(MEAN_SECONDS, share(rs, r -> r.elapsed().toNanos() / NANOS_PER_SECOND));
    out.put(TRUTH_ALIGNMENT, truthAlignment(rs));
    out.put(CHOSEN_TOP3, chosenInTop3(rs));
    out.put(
        HOURS_PARSED,
        ratio(
            rs.stream().mapToDouble(r -> r.evidence().hoursParsed()).sum(),
            rs.stream().mapToDouble(r -> r.evidence().hoursDeclared()).sum()));
    return Collections.unmodifiableMap(out);
  }

  /**
   * Computes the eleven metrics of {@link #of(List)} followed by the five calibration metrics of
   * {@link #CALIBRATION_NAMES}.
   *
   * @param rs runs of one experiment
   * @param gold gold entries by {@code coupleId}; couples without entry are left out of the gold
   *     metrics
   * @return every metric of {@link #NAMES} and {@link #CALIBRATION_NAMES}, in that order; NaN where
   *     there is no data
   * @implNote O(r · (s log s + k) + c · q² · n) time and O(r · n) space, r runs, s sample size, k
   *     picked ids, c couples, q seeds per couple, n final size.
   */
  public static Map<String, Double> of(List<RunRecord> rs, Map<String, GoldEntry> gold) {
    Map<String, Double> out = new LinkedHashMap<>(of(rs));
    out.put(GOLD_HIT, gold(rs, gold, Metrics::hits));
    out.put(GOLD_VIOLATION, gold(rs, gold, Metrics::violates));
    out.put(FAIRNESS_GAP, fairnessGap(rs));
    out.put(STABILITY, stabilityAtSeed(rs));
    out.put(CALLS_PER_RUN, share(rs, r -> r.evidence().calls()));
    return Collections.unmodifiableMap(out);
  }

  /**
   * Jaccard index of two id lists taken as sets; two empty lists are identical (1).
   *
   * @param x first list
   * @param y second list
   * @return {@code |x ∩ y| / |x ∪ y|} in {@code [0, 1]}
   * @implNote O(|x| + |y|) time and space.
   */
  static double jaccard(Collection<String> x, Collection<String> y) {
    Set<String> union = new HashSet<>(x);
    union.addAll(y);
    if (union.isEmpty()) {
      return 1.0;
    }
    Set<String> inter = new HashSet<>(x);
    inter.retainAll(new HashSet<>(y));
    return (double) inter.size() / union.size();
  }

  private static double gold(
      List<RunRecord> rs, Map<String, GoldEntry> gold, BiPredicate<RunRecord, GoldEntry> test) {
    return rs.stream()
        .filter(r -> gold.containsKey(r.coupleId()))
        .mapToDouble(r -> test.test(r, gold.get(r.coupleId())) ? 1 : 0)
        .average()
        .orElse(Double.NaN);
  }

  private static boolean hits(RunRecord r, GoldEntry g) {
    return r.evidence().finalTypes().stream().anyMatch(g.expectedTypes()::contains);
  }

  private static boolean violates(RunRecord r, GoldEntry g) {
    return r.evidence().finalTypes().stream().anyMatch(g.forbiddenTypes()::contains)
        || r.gate().finalIds().stream().anyMatch(g.forbiddenIds()::contains);
  }

  private static double fairnessGap(List<RunRecord> rs) {
    return rs.stream()
        .map(Metrics::gap)
        .flatMap(Optional::stream)
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(Double.NaN);
  }

  private static Optional<Double> gap(RunRecord r) {
    Map<Agent, Map<Feature, Double>> truth = r.evidence().truthWeights();
    if (!truth.containsKey(Agent.A) || !truth.containsKey(Agent.B)) {
      return Optional.empty();
    }
    List<Map<Feature, Double>> outcome = outcome(r);
    if (outcome.isEmpty()) {
      return Optional.empty();
    }
    double a = alignment(outcome, truth.get(Agent.A));
    double b = alignment(outcome, truth.get(Agent.B));
    return Optional.of(Math.abs(a - b));
  }

  /**
   * Sample features of the outcome of a run: the chosen activity when there is a decision, every
   * final id otherwise; ids absent from the sample are skipped.
   */
  private static List<Map<Feature, Double>> outcome(RunRecord r) {
    List<String> ids = r.decision().map(d -> List.of(d.chosen())).orElse(r.gate().finalIds());
    Map<String, Map<Feature, Double>> features = new HashMap<>();
    for (SampleItem item : r.sample().items()) {
      features.putIfAbsent(item.activityId(), item.features());
    }
    return ids.stream().filter(features::containsKey).map(features::get).toList();
  }

  /** τ_X: mean truth score of the outcome under {@code truth}, in [0, 1]. */
  private static double alignment(List<Map<Feature, Double>> outcome, Map<Feature, Double> truth) {
    return outcome.stream().mapToDouble(x -> Scorer.score(x, truth)).average().orElse(0.0);
  }

  private static double stabilityAtSeed(List<RunRecord> rs) {
    Map<String, Map<Long, List<List<String>>>> byCouple = new LinkedHashMap<>();
    for (RunRecord r : rs) {
      byCouple
          .computeIfAbsent(r.coupleId(), c -> new LinkedHashMap<>())
          .computeIfAbsent(r.seed(), s -> new ArrayList<>())
          .add(r.gate().finalIds());
    }
    return byCouple.values().stream()
        .map(Metrics::coupleStability)
        .flatMap(Optional::stream)
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(Double.NaN);
  }

  /** Mean Jaccard over the pairs of distinct seeds of one couple, runs paired by position. */
  private static Optional<Double> coupleStability(Map<Long, List<List<String>>> bySeed) {
    List<List<List<String>>> seeds = new ArrayList<>(bySeed.values());
    double sum = 0.0;
    int pairs = 0;
    for (int i = 0; i < seeds.size(); i++) {
      for (int j = i + 1; j < seeds.size(); j++) {
        int n = Math.min(seeds.get(i).size(), seeds.get(j).size());
        for (int it = 0; it < n; it++) {
          sum += jaccard(seeds.get(i).get(it), seeds.get(j).get(it));
          pairs++;
        }
      }
    }
    return pairs == 0 ? Optional.empty() : Optional.of(sum / pairs);
  }

  /**
   * Cosine similarity of two weight vectors over every feature (missing features count as 0).
   *
   * @param w first vector
   * @param v second vector
   * @return the cosine, or empty when a vector has zero norm
   * @implNote O(f) time and O(1) space, f = features.
   */
  static Optional<Double> cosine(Map<Feature, Double> w, Map<Feature, Double> v) {
    double dot = 0.0;
    double nw = 0.0;
    double nv = 0.0;
    for (Feature f : Feature.values()) {
      double x = w.getOrDefault(f, 0.0);
      double y = v.getOrDefault(f, 0.0);
      dot += x * y;
      nw += x * x;
      nv += y * y;
    }
    if (nw == 0.0 || nv == 0.0) {
      return Optional.empty();
    }
    return Optional.of(dot / Math.sqrt(nw * nv));
  }

  private static double share(List<RunRecord> rs, ToDoubleFunction<RunRecord> value) {
    return rs.stream().mapToDouble(value).average().orElse(Double.NaN);
  }

  private static double ratio(double num, double den) {
    return den == 0.0 ? Double.NaN : num / den;
  }

  private static double hallucination(List<RunRecord> rs) {
    List<Pick> picks =
        rs.stream()
            .map(RunRecord::gate)
            .flatMap(g -> Stream.concat(g.round1().stream(), g.round2().stream()))
            .toList();
    double invented = picks.stream().mapToDouble(p -> p.hallucinated().size()).sum();
    double valid = picks.stream().mapToDouble(p -> p.ids().size()).sum();
    return ratio(invented, invented + valid);
  }

  private static double typeDiversity(List<RunRecord> rs) {
    return rs.stream()
        .map(r -> r.evidence().finalTypes())
        .filter(types -> !types.isEmpty())
        .mapToDouble(types -> (double) new HashSet<>(types).size() / types.size())
        .average()
        .orElse(Double.NaN);
  }

  private static double truthAlignment(List<RunRecord> rs) {
    return rs.stream()
        .filter(r -> r.after().isPresent())
        .map(r -> meanCosine(r.after().get().w(), r.evidence().truthWeights().values()))
        .flatMap(Optional::stream)
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(Double.NaN);
  }

  private static Optional<Double> meanCosine(
      Map<Feature, Double> w, Collection<Map<Feature, Double>> truths) {
    double[] cos =
        truths.stream()
            .map(t -> cosine(w, t))
            .flatMap(Optional::stream)
            .mapToDouble(Double::doubleValue)
            .toArray();
    if (cos.length == 0) {
      return Optional.empty();
    }
    double sum = 0.0;
    for (double c : cos) {
      sum += c;
    }
    return Optional.of(sum / cos.length);
  }

  private static double chosenInTop3(List<RunRecord> rs) {
    int hits = 0;
    int total = 0;
    for (RunRecord r : rs) {
      for (Pick pick : r.gate().round1()) {
        Map<Feature, Double> truth = r.evidence().truthWeights().get(pick.agent());
        if (pick.agent() != Agent.M && pick.ok() && !pick.ids().isEmpty() && truth != null) {
          total++;
          hits += topByTruth(r.sample().items(), truth).contains(pick.ids().get(0)) ? 1 : 0;
        }
      }
    }
    return ratio(hits, total);
  }

  private static List<String> topByTruth(List<SampleItem> items, Map<Feature, Double> truth) {
    return items.stream()
        .sorted(
            Comparator.comparingDouble((SampleItem i) -> -Scorer.score(i.features(), truth))
                .thenComparing(SampleItem::activityId))
        .map(SampleItem::activityId)
        .limit(TOP)
        .toList();
  }
}
