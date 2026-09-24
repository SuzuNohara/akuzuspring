package com.nexus.nexussync.rounds;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.params.Fill;
import com.nexus.nexussync.params.Intersection;
import com.nexus.nexussync.params.RankAggregation;
import com.nexus.nexussync.params.RoundsParams;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClosersTest {

  private static final Pick A = ok(Agent.A, "s1", "s2", "s3", "s4", "s5");
  private static final Pick B = ok(Agent.B, "s5", "s3", "s1", "s6", "s7");
  private static final Pick M = ok(Agent.M, "s1", "s8", "s5", "s9");
  private static final Pick M_FAILED = Pick.failed(Agent.M, List.of());

  static Pick ok(Agent agent, String... ids) {
    List<String> reasons = new ArrayList<>();
    for (int i = 0; i < ids.length; i++) {
      reasons.add("");
    }
    return new Pick(agent, List.of(ids), reasons, List.of(), PickStatus.OK);
  }

  private static RoundsParams params(Intersection intersection, Fill fill) {
    return new RoundsParams(5, 5, 5, intersection, 5, fill, RankAggregation.RANK_SUM, 2, true);
  }

  // U7-04
  @Test
  void given_triple_when_f1_then_intersectionOfThreeInOrderOfA() {
    assertThat(Closers.f1(A, B, M, params(Intersection.TRIPLE, Fill.NONE)))
        .containsExactly("s1", "s5");
  }

  // U7-04
  @Test
  void given_pairMediatorTiebreak_when_f1_then_pairWithMediatorIdsFirst() {
    assertThat(Closers.f1(A, B, M, params(Intersection.PAIR_MEDIATOR_TIEBREAK, Fill.NONE)))
        .containsExactly("s1", "s5", "s3");
  }

  // U7-04
  @Test
  void given_failedMediator_when_f1_then_pairIntersection() {
    assertThat(Closers.f1(A, B, M_FAILED, params(Intersection.TRIPLE, Fill.NONE)))
        .containsExactly("s1", "s3", "s5");
    assertThat(Closers.f1(A, B, M_FAILED, params(Intersection.PAIR_MEDIATOR_TIEBREAK, Fill.NONE)))
        .containsExactly("s1", "s3", "s5");
  }

  // U7-04
  @Test
  void given_failedPersona_when_intersect_then_empty() {
    assertThat(Closers.intersect(A, Pick.failed(Agent.B, List.of()))).isEmpty();
    assertThat(Closers.intersect(Pick.failed(Agent.A, List.of()), B)).isEmpty();
  }

  // U7-06
  @Test
  void given_sharedVotes_when_f2_then_f1FollowedBySharedVotes() {
    Pick va = ok(Agent.A, "s6", "s1", "s7", "s3");
    Pick vb = ok(Agent.B, "s3", "s7", "s9");

    assertThat(Closers.f2(List.of("s1", "s5"), va, vb)).containsExactly("s1", "s5", "s7", "s3");
  }

  // U7-06
  @Test
  void given_failedVote_when_f2_then_f1Unchanged() {
    assertThat(Closers.f2(List.of("s1"), ok(Agent.A, "s2"), Pick.failed(Agent.B, List.of())))
        .containsExactly("s1");
  }

  // U7-07
  @Test
  void given_alternateAb_when_f3_then_takesAlternatelyFromBothPersonas() {
    assertThat(Closers.f3(List.of("s1"), A, B, M, params(Intersection.TRIPLE, Fill.ALTERNATE_AB)))
        .containsExactly("s1", "s5", "s2", "s3", "s4");
  }

  // U7-07
  @Test
  void given_mediatorFirst_when_f3_then_takesMediatorBeforeAlternating() {
    assertThat(Closers.f3(List.of("s3"), A, B, M, params(Intersection.TRIPLE, Fill.MEDIATOR_FIRST)))
        .containsExactly("s3", "s1", "s8", "s5", "s9");
  }

  // U7-07
  @Test
  void given_mediatorFirstAndFailedMediator_when_f3_then_behavesAsAlternate() {
    assertThat(
            Closers.f3(
                List.of("s1"), A, B, M_FAILED, params(Intersection.TRIPLE, Fill.MEDIATOR_FIRST)))
        .containsExactly("s1", "s5", "s2", "s3", "s4");
  }

  // U7-07
  @Test
  void given_fillNone_when_f3_then_currentUnchanged() {
    assertThat(Closers.f3(List.of("s1", "s2"), A, B, M, params(Intersection.TRIPLE, Fill.NONE)))
        .containsExactly("s1", "s2");
  }

  // U7-07
  @Test
  void given_shortPicks_when_f3_then_stopsWhenPicksRunOut() {
    Pick a = ok(Agent.A, "s1", "s2");
    Pick b = ok(Agent.B, "s3");

    assertThat(
            Closers.f3(List.of(), a, b, M_FAILED, params(Intersection.TRIPLE, Fill.ALTERNATE_AB)))
        .containsExactly("s1", "s3", "s2");
  }

  // U7-07
  @Test
  void given_currentAlreadyFull_when_f3_then_addsNothing() {
    List<String> full = List.of("x1", "x2", "x3", "x4", "x5");

    assertThat(Closers.f3(full, A, B, M, params(Intersection.TRIPLE, Fill.MEDIATOR_FIRST)))
        .containsExactlyElementsOf(full);
  }
}
