package com.nexus.nexussync.decision;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.Profile;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.SamplerParams;
import com.nexus.nexussync.sampler.FeatureExtractor;
import com.nexus.nexussync.sampler.Scorer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Simulated ranking of one person over the final options, used by the bench (unit U8).
 *
 * <p>The score of every final id is {@code Scorer.score(FeatureExtractor.of(...), truthWeights)}
 * plus, when {@code truthNoise > 0}, a Gaussian perturbation {@code rng.nextGaussian() *
 * truthNoise} (A1). With {@code truthNoise == 0} the random source is not consumed and the ranking
 * is deterministic. Ties are broken lexicographically by id. The random source is a reproducible
 * simulation input, not a security primitive.
 */
public final class TruthRanker {

  /** Maximum ranking length. */
  static final int MAX_RANK = 3;

  private static final Comparator<Scored> ORDER =
      Comparator.comparingDouble(Scored::score).reversed().thenComparing(Scored::id);

  private TruthRanker() {}

  /**
   * Ranks the final ids by the hidden preference weights of {@code profile}.
   *
   * @param finalIds final options of the gate, never {@code null}
   * @param profile person whose {@code truthWeights} drive the ranking
   * @param cat catalog holding every final id
   * @param ctx context of the couple
   * @param p sampler parameters used by the feature extractor
   * @param truthNoise standard deviation of the Gaussian noise on the score, {@code >= 0}
   * @param rng seeded source of the noise, never {@code null}
   * @return the best {@code min(3, finalIds.size())} ids, best first
   * @throws DecisionException if the profile has no {@code truthWeights}, {@code truthNoise} is
   *     negative or not a number, or a final id is missing from the catalog
   * @implNote O(n log n) time and O(n) space, n = number of final ids.
   */
  public static List<String> rank(
      List<String> finalIds,
      Profile profile,
      Catalog cat,
      Context ctx,
      SamplerParams p,
      double truthNoise,
      Random rng)
      throws DecisionException {
    Map<Feature, Double> w =
        profile
            .truthWeights()
            .orElseThrow(
                () ->
                    new DecisionException("profile " + profile.userId() + " has no truthWeights"));
    if (!(truthNoise >= 0.0)) {
      throw new DecisionException("truthNoise must be >= 0: " + truthNoise);
    }
    List<Scored> scored = new ArrayList<>(finalIds.size());
    for (String id : finalIds) {
      Activity a =
          Optional.ofNullable(cat.activities().get(id))
              .orElseThrow(() -> new DecisionException("final id not in catalog: " + id));
      double s = Scorer.score(FeatureExtractor.of(a, ctx, cat, p), w);
      if (truthNoise > 0.0) {
        s += rng.nextGaussian() * truthNoise;
      }
      scored.add(new Scored(id, s));
    }
    scored.sort(ORDER);
    return scored.stream().limit(MAX_RANK).map(Scored::id).toList();
  }

  private record Scored(String id, double score) {}
}
