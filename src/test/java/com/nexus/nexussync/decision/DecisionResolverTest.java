package com.nexus.nexussync.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.params.DecisionParams;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DecisionResolverTest {

  private static final DecisionParams PARAMS =
      new DecisionParams(List.of(3, 2, 1), 5, 48, 0.5, 0.5, 30);
  private static final List<String> FINAL = List.of("e", "d", "c", "b", "a");

  // U8-01
  @Test
  void givenClearWinner_whenResolve_thenHighestPointsChosenWithoutTie() throws Exception {
    Decision d =
        DecisionResolver.resolve(
            FINAL, List.of("a", "b", "c"), List.of("a", "c", "b"), PARAMS, new Random(1));

    assertThat(d.chosen()).isEqualTo("a");
    assertThat(d.tie()).isFalse();
    assertThat(d.tieCandidates()).isEmpty();
    assertThat(d.scores()).containsEntry("a", 6).containsEntry("b", 3).containsEntry("c", 3);
    assertThat(d.rankA()).containsExactly("a", "b", "c");
    assertThat(d.rankB()).containsExactly("a", "c", "b");
  }

  // U8-02
  @Test
  void givenUnrankedFinalIds_whenResolve_thenScoresIncludeThemWithZero() throws Exception {
    Decision d =
        DecisionResolver.resolve(
            FINAL, List.of("a", "b", "c"), List.of("a", "c", "b"), PARAMS, new Random(1));

    assertThat(d.scores()).containsOnlyKeys(FINAL).containsEntry("d", 0).containsEntry("e", 0);
    assertThat(d.scores().keySet()).containsExactlyElementsOf(FINAL);
  }

  // U8-03
  @Test
  void givenTieAtMaximum_whenResolve_thenSortedCandidatesAndSeededChoice() throws Exception {
    List<String> rankA = List.of("c", "a", "b");
    List<String> rankB = List.of("a", "c", "b");
    List<String> candidates = List.of("a", "c");

    Decision d = DecisionResolver.resolve(FINAL, rankA, rankB, PARAMS, new Random(42));

    assertThat(d.tie()).isTrue();
    assertThat(d.tieCandidates()).containsExactlyElementsOf(candidates);
    assertThat(d.chosen()).isEqualTo(candidates.get(new Random(42).nextInt(2)));
    for (long seed = 0; seed < 20; seed++) {
      String first =
          DecisionResolver.resolve(FINAL, rankA, rankB, PARAMS, new Random(seed)).chosen();
      String second =
          DecisionResolver.resolve(FINAL, rankA, rankB, PARAMS, new Random(seed)).chosen();
      assertThat(first).isEqualTo(second).isIn(candidates);
    }
  }

  // U8-04
  @Test
  void givenInvalidRankings_whenResolve_thenDecisionException() {
    assertRejected(List.of("a", "b", "z"), List.of("a", "b", "c"));
    assertRejected(List.of("a", "b", "c"), List.of("a", "a", "c"));
    assertRejected(List.of("a", "b"), List.of("a", "b", "c"));
    assertRejected(List.of("a", "b", "c"), List.of("a", "b", "c", "d"));
  }

  // U8-05
  @Test
  void givenTwoFinalIds_whenResolve_thenRankingsOfTwoAreRequired() throws Exception {
    List<String> two = List.of("x", "y");

    Decision d =
        DecisionResolver.resolve(two, List.of("y", "x"), List.of("y", "x"), PARAMS, new Random(3));

    assertThat(d.chosen()).isEqualTo("y");
    assertThat(d.scores()).containsEntry("y", 6).containsEntry("x", 4);
    assertThatThrownBy(
            () ->
                DecisionResolver.resolve(
                    two, List.of("y", "x", "y"), List.of("y", "x"), PARAMS, new Random(3)))
        .isInstanceOf(DecisionException.class);
  }

  @Test
  void givenNoFinalIds_whenResolve_thenDecisionException() {
    assertThatThrownBy(
            () -> DecisionResolver.resolve(List.of(), List.of(), List.of(), PARAMS, new Random(1)))
        .isInstanceOf(DecisionException.class);
  }

  @Test
  void givenTooFewRankPoints_whenResolve_thenDecisionException() {
    DecisionParams shortPoints = new DecisionParams(List.of(3, 2), 5, 48, 0.5, 0.5, 30);

    assertThatThrownBy(
            () ->
                DecisionResolver.resolve(
                    FINAL,
                    List.of("a", "b", "c"),
                    List.of("a", "b", "c"),
                    shortPoints,
                    new Random(1)))
        .isInstanceOf(DecisionException.class)
        .hasMessageContaining("rank points");
  }

  @Test
  void givenDecision_whenMutatingCollections_thenUnsupported() throws Exception {
    Decision d =
        DecisionResolver.resolve(
            FINAL, List.of("c", "a", "b"), List.of("a", "c", "b"), PARAMS, new Random(1));

    assertThatThrownBy(() -> d.scores().put("z", 1))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> d.tieCandidates().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static void assertRejected(List<String> rankA, List<String> rankB) {
    assertThatThrownBy(() -> DecisionResolver.resolve(FINAL, rankA, rankB, PARAMS, new Random(1)))
        .isInstanceOf(DecisionException.class);
  }
}
