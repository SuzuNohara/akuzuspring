package com.nexus.nexussync.bench;

import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.rounds.Agent;
import com.nexus.nexussync.rounds.Closure;
import com.nexus.nexussync.rounds.Pick;
import com.nexus.nexussync.sampler.SampleItem;
import com.nexus.nexussync.sampler.Scorer;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 *       the top 3 of the sample ordered by the score with that person's {@code truthWeights}.
 *   <li>{@value #HOURS_PARSED}: proposed places with interpreted hours over proposed places with
 *       non-empty hours.
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
        if (pick.agent() != Agent.M && pick.ok() && truth != null) {
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
