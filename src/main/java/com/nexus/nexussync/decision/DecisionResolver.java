package com.nexus.nexussync.decision;

import com.nexus.nexussync.params.DecisionParams;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Resolves the couple decision from the two personal rankings (unit U8).
 *
 * <p>Each person ranks {@code min(3, finalIds.size())} distinct final ids; position {@code i} is
 * worth {@code rankPoints.get(i)}. The highest total wins; a tie at the maximum is broken by the
 * given {@link Random}, so the same seed always yields the same decision. The random source is a
 * reproducible simulation input, not a security primitive.
 */
public final class DecisionResolver {

  /** Maximum ranking length asked to each person. */
  static final int MAX_RANK = 3;

  private DecisionResolver() {}

  /**
   * Scores both rankings and picks the winner.
   *
   * @param finalIds final options of the gate, never {@code null}
   * @param rankA ranking of person A, best first, never {@code null}
   * @param rankB ranking of person B, best first, never {@code null}
   * @param p decision parameters with the points per position, never {@code null}
   * @param rng seeded source used only to break a tie at the maximum, never {@code null}
   * @return the decision with scores for every final id
   * @throws DecisionException if there are no final ids, a ranking has the wrong size, repeats an
   *     id or names an id outside {@code finalIds}, or there are fewer rank points than positions
   * @implNote O(n log n) time and O(n) space, n = number of final ids.
   */
  public static Decision resolve(
      List<String> finalIds, List<String> rankA, List<String> rankB, DecisionParams p, Random rng)
      throws DecisionException {
    if (finalIds.isEmpty()) {
      throw new DecisionException("no final ids to decide on");
    }
    int size = Math.min(MAX_RANK, finalIds.size());
    if (p.rankPoints().size() < size) {
      throw new DecisionException(
          "rank points " + p.rankPoints() + " do not cover " + size + " positions");
    }
    Set<String> allowed = new HashSet<>(finalIds);
    validate("A", rankA, allowed, size);
    validate("B", rankB, allowed, size);
    Map<String, Integer> scores = new LinkedHashMap<>();
    finalIds.forEach(id -> scores.put(id, 0));
    addPoints(scores, rankA, p.rankPoints());
    addPoints(scores, rankB, p.rankPoints());
    List<String> best = leaders(scores);
    boolean tie = best.size() > 1;
    String chosen = tie ? best.get(rng.nextInt(best.size())) : best.get(0);
    return new Decision(chosen, scores, rankA, rankB, tie, tie ? best : List.of());
  }

  private static void validate(String who, List<String> rank, Set<String> allowed, int size)
      throws DecisionException {
    if (rank.size() != size) {
      throw new DecisionException(
          "ranking " + who + " has " + rank.size() + " ids, expected " + size);
    }
    Set<String> seen = new HashSet<>();
    for (String id : rank) {
      if (!allowed.contains(id)) {
        throw new DecisionException("ranking " + who + " names an id outside the final: " + id);
      }
      if (!seen.add(id)) {
        throw new DecisionException("ranking " + who + " repeats id: " + id);
      }
    }
  }

  private static void addPoints(
      Map<String, Integer> scores, List<String> rank, List<Integer> points) {
    for (int pos = 0; pos < rank.size(); pos++) {
      scores.merge(rank.get(pos), points.get(pos), Integer::sum);
    }
  }

  private static List<String> leaders(Map<String, Integer> scores) {
    int max = Collections.max(scores.values());
    List<String> best = new ArrayList<>();
    scores.forEach(
        (id, score) -> {
          if (score == max) {
            best.add(id);
          }
        });
    Collections.sort(best);
    return best;
  }
}
