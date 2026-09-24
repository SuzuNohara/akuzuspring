package com.nexus.nexussync.rounds;

import static com.nexus.nexussync.rounds.ClosersTest.ok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.nexus.nexussync.params.Fill;
import com.nexus.nexussync.params.Intersection;
import com.nexus.nexussync.params.RankAggregation;
import com.nexus.nexussync.params.RoundsParams;
import java.util.List;
import org.junit.jupiter.api.Test;

class RankAggregatorTest {

  private static final Pick A = ok(Agent.A, "s1", "s2", "s3");
  private static final Pick B = ok(Agent.B, "s3", "s2", "s1");
  private static final Pick M = ok(Agent.M, "s4", "s3");
  private static final List<Pick> PICKS = List.of(A, B, M, Pick.failed(Agent.A, List.of("x")));

  private static RoundsParams params(RankAggregation aggregation) {
    return new RoundsParams(3, 3, 5, Intersection.TRIPLE, 5, Fill.NONE, aggregation, 2, true);
  }

  // U7-08
  @Test
  void given_okAndFailedPicks_when_rankSum_then_positionsOrMissingRankOverOkPicksOnly() {
    assertThat(RankAggregator.rankSum(List.of("s1", "s2", "s3", "s4", "s9"), PICKS, 3))
        .containsExactly(
            entry("s1", 1 + 3 + 4),
            entry("s2", 2 + 2 + 4),
            entry("s3", 3 + 1 + 2),
            entry("s4", 4 + 4 + 1),
            entry("s9", 12));
  }

  // U7-08
  @Test
  void given_rankSumWithTies_when_order_then_ascendingSumThenLexicographic() {
    assertThat(
            RankAggregator.order(
                List.of("s4", "s2", "s1", "s3", "s1"), PICKS, params(RankAggregation.RANK_SUM)))
        .containsExactly("s3", "s1", "s2", "s4");
  }

  // U7-08
  @Test
  void given_borda_when_order_then_descendingBordaThenLexicographic() {
    assertThat(
            RankAggregator.order(
                List.of("s4", "s2", "s1", "s3"), PICKS, params(RankAggregation.BORDA)))
        .containsExactly("s3", "s1", "s2", "s4");
  }

  // U7-08
  @Test
  void given_mediatorPriority_when_order_then_mediatorIdsFirstThenRankSum() {
    assertThat(
            RankAggregator.order(
                List.of("s1", "s2", "s3", "s4"), PICKS, params(RankAggregation.MEDIATOR_PRIORITY)))
        .containsExactly("s4", "s3", "s1", "s2");
  }

  // U7-08
  @Test
  void given_mediatorPriorityWithoutMediator_when_order_then_rankSum() {
    assertThat(
            RankAggregator.order(
                List.of("s1", "s2", "s3"),
                List.of(A, B),
                params(RankAggregation.MEDIATOR_PRIORITY)))
        .containsExactly("s1", "s2", "s3");
  }

  // U7-08
  @Test
  void given_finalIds_when_remaining_then_pickedByRankSumThenUnpickedInSampleOrder() {
    List<String> sample = List.of("s9", "s1", "s2", "s3", "s4", "s8");

    assertThat(RankAggregator.remaining(sample, List.of("s3"), PICKS))
        .containsExactly("s1", "s2", "s4", "s9", "s8");
  }

  // U7-08
  @Test
  void given_noPicks_when_remaining_then_sampleOrder() {
    assertThat(RankAggregator.remaining(List.of("s2", "s1", "s2"), List.of(), List.of()))
        .containsExactly("s2", "s1");
  }
}
