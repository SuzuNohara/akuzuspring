package com.nexus.nexussync.rounds;

import com.nexus.nexussync.params.RankAggregation;
import com.nexus.nexussync.params.RoundsParams;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Aggregation of the individual rankings of the agents (unit U7).
 *
 * <p>Only {@link PickStatus#OK} picks take part. Every tie is broken by lexicographic order of the
 * id, so the result never depends on hash iteration order.
 */
public final class RankAggregator {

  private RankAggregator() {}

  /**
   * Rank sum of each id: its 1-based position in every OK pick, or {@code k + 1} when absent.
   *
   * @param ids ids to score
   * @param picks rankings to aggregate
   * @param k size of a full ranking
   * @return rank sum per id, in the iteration order of {@code ids}
   * @implNote O(|ids| · P + Σ|pick|) time, O(|ids| + Σ|pick|) space, P = number of picks.
   */
  static Map<String, Integer> rankSum(Collection<String> ids, List<Pick> picks, int k) {
    List<Map<String, Integer>> positions =
        picks.stream().filter(Pick::ok).map(RankAggregator::positions).toList();
    Map<String, Integer> out = new LinkedHashMap<>();
    for (String id : ids) {
      int sum = 0;
      for (Map<String, Integer> pos : positions) {
        sum += pos.getOrDefault(id, k + 1);
      }
      out.put(id, sum);
    }
    return out;
  }

  /**
   * Orders the ids according to {@link RoundsParams#rankAggregation()}.
   *
   * <p>{@link RankAggregation#RANK_SUM}: ascending rank sum. {@link RankAggregation#BORDA}:
   * descending Borda score ({@code k + 1 − position}, 0 when absent). {@link
   * RankAggregation#MEDIATOR_PRIORITY}: ids recommended by the mediator first, in its order, then
   * the rest by rank sum. {@code k} is {@link RoundsParams#pickCount()}.
   *
   * @param ids ids to order (duplicates are ignored)
   * @param picks round-one picks
   * @param p gate parameters
   * @return the ordered ids
   * @implNote O(n log n + |ids| · P) time, O(n) space.
   */
  static List<String> order(Collection<String> ids, List<Pick> picks, RoundsParams p) {
    int k = p.pickCount();
    Set<String> unique = new LinkedHashSet<>(ids);
    if (p.rankAggregation() == RankAggregation.BORDA) {
      Map<String, Integer> sums = rankSum(unique, picks, k);
      int okPicks = (int) picks.stream().filter(Pick::ok).count();
      Comparator<String> borda =
          Comparator.comparingInt((String id) -> okPicks * (k + 1) - sums.get(id));
      return sorted(unique, borda.reversed().thenComparing(Comparator.naturalOrder()));
    }
    List<String> out = new ArrayList<>();
    if (p.rankAggregation() == RankAggregation.MEDIATOR_PRIORITY) {
      mediator(picks).ifPresent(m -> m.ids().stream().filter(unique::contains).forEach(out::add));
      unique.removeAll(out);
    }
    Map<String, Integer> sums = rankSum(unique, picks, k);
    out.addAll(
        sorted(
            unique,
            Comparator.comparingInt((String id) -> sums.get(id))
                .thenComparing(Comparator.naturalOrder())));
    return out;
  }

  /**
   * Sample ids that did not make the final list: first the ids picked by some agent, by ascending
   * rank sum (ties lexicographic), then the never-picked ids in sample order.
   *
   * @param sampleIds ids of the sample, in sample order
   * @param finalIds final list of the gate
   * @param picks round-one picks
   * @return the remaining ids, most promising first
   * @implNote O(n log n + n · P) time, O(n) space, n = |sampleIds|.
   */
  static List<String> remaining(List<String> sampleIds, List<String> finalIds, List<Pick> picks) {
    Set<String> excluded = new HashSet<>(finalIds);
    Set<String> picked = new HashSet<>();
    picks.stream().filter(Pick::ok).forEach(pk -> picked.addAll(pk.ids()));
    List<String> candidates =
        sampleIds.stream().filter(id -> !excluded.contains(id)).distinct().toList();
    int k = picks.stream().mapToInt(pk -> pk.ids().size()).max().orElse(0);
    List<String> chosen = candidates.stream().filter(picked::contains).toList();
    Map<String, Integer> sums = rankSum(chosen, picks, k);
    List<String> out =
        new ArrayList<>(
            sorted(
                chosen,
                Comparator.comparingInt((String id) -> sums.get(id))
                    .thenComparing(Comparator.naturalOrder())));
    candidates.stream().filter(id -> !picked.contains(id)).forEach(out::add);
    return out;
  }

  private static Map<String, Integer> positions(Pick pick) {
    Map<String, Integer> pos = new LinkedHashMap<>();
    for (int i = 0; i < pick.ids().size(); i++) {
      pos.putIfAbsent(pick.ids().get(i), i + 1);
    }
    return pos;
  }

  private static Optional<Pick> mediator(List<Pick> picks) {
    return picks.stream().filter(pk -> pk.agent() == Agent.M && pk.ok()).findFirst();
  }

  private static List<String> sorted(Collection<String> ids, Comparator<String> cmp) {
    return ids.stream().sorted(cmp).toList();
  }
}
