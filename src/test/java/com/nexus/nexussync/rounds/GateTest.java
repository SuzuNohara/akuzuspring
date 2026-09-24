package com.nexus.nexussync.rounds;

import static com.nexus.nexussync.rounds.FakeGateExecutor.envelope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.params.Fill;
import com.nexus.nexussync.params.Intersection;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.params.RankAggregation;
import com.nexus.nexussync.params.RoundsParams;
import com.nexus.nexussync.sampler.Sample;
import com.nexus.nexussync.sampler.SampleItem;
import com.nexus.nexussync.sampler.SampleStatus;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GateTest {

  private static final Path RUN_DIR = Path.of("runs", "gate-test");
  private static final Context CTX =
      new Context("1-2", null, null, null, null, List.of(), Map.of(), 0, LocalDate.of(2026, 9, 27));
  private static final List<String> F1_A = List.of("s01", "s02", "s03", "s04", "s05");

  private final Gate gate = new Gate();

  private static Sample sample() {
    List<SampleItem> items = new ArrayList<>();
    for (int i = 10; i >= 1; i--) {
      items.add(new SampleItem(String.format("s%02d", i), 1.0 - i * 0.05, Map.of(), false));
    }
    return new Sample(items, List.of(), SampleStatus.OK, Map.of());
  }

  private static RoundsParams rounds(Fill fill, int voteCount, int maxRounds, boolean allowTwoAi) {
    return new RoundsParams(
        5,
        voteCount,
        5,
        Intersection.TRIPLE,
        5,
        fill,
        RankAggregation.RANK_SUM,
        maxRounds,
        allowTwoAi);
  }

  private static RoundsParams rounds() {
    return rounds(Fill.ALTERNATE_AB, 5, 2, true);
  }

  private static Params params(RoundsParams rp) {
    return new Params(
        "gate-test",
        7L,
        Path.of("catalog"),
        Path.of("places.csv"),
        null,
        rp,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private GateResult run(FakeGateExecutor ex, RoundsParams rp, CallBudget budget)
      throws AnnException {
    return gate.run(sample(), CTX, params(rp), ex, RUN_DIR, budget);
  }

  private static FakeGateExecutor f1Setup() {
    return new FakeGateExecutor()
        .respond(Agent.A, 1, envelope("a", "picks", F1_A.toArray(String[]::new)))
        .respond(Agent.B, 1, envelope("b", "picks", "s05", "s04", "s03", "s02", "s01"))
        .respond(Agent.M, 1, envelope("m", "picks", "s03", "s01", "s02", "s05", "s04"));
  }

  private static FakeGateExecutor f2Setup() {
    return new FakeGateExecutor()
        .respond(Agent.A, 1, envelope("a", "picks", F1_A.toArray(String[]::new)))
        .respond(Agent.B, 1, envelope("b", "picks", "s01", "s02", "s06", "s07", "s08"))
        .respond(Agent.M, 1, envelope("m", "picks", "s01", "s02", "s09", "s03", "s06"));
  }

  // U7-05
  @Test
  void given_intersectionReachesThreshold_when_run_then_closesF1OrderedByRankSum()
      throws AnnException {
    FakeGateExecutor ex = f1Setup();
    CallBudget budget = new CallBudget(200);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.F1);
    assertThat(r.finalIds()).containsExactly("s03", "s01", "s02", "s05", "s04");
    assertThat(r.rankSums())
        .containsExactly(
            entry("s03", 7), entry("s01", 8), entry("s02", 9), entry("s05", 10), entry("s04", 11));
    assertThat(r.remaining()).containsExactly("s10", "s09", "s08", "s07", "s06");
    assertThat(r.reasons().get("s03"))
        .containsExactly(entry(Agent.A, "s03-a"), entry(Agent.B, "s03-b"), entry(Agent.M, "s03-m"));
    assertThat(r.round1()).extracting(Pick::agent).containsExactly(Agent.A, Agent.B, Agent.M);
    assertThat(r.round2()).isEmpty();
    assertThat(ex.round1Calls()).isEqualTo(1);
    assertThat(ex.round2Calls()).isZero();
    assertThat(budget.used()).isEqualTo(3);
  }

  // U7-05
  @Test
  void given_sharedVotesReachThreshold_when_run_then_closesF2() throws AnnException {
    FakeGateExecutor ex =
        f2Setup()
            .respond(Agent.A, 2, envelope("a", "votes", "s03", "s04", "s05", "s06", "s07"))
            .respond(Agent.B, 2, envelope("b", "votes", "s03", "s04", "s05", "s06", "s08"));
    CallBudget budget = new CallBudget(200);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.F2);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s06", "s04");
    assertThat(r.remaining()).containsExactly("s09", "s07", "s05", "s08", "s10");
    assertThat(ex.shortlists())
        .containsExactly(List.of("s03", "s04", "s05", "s06", "s07", "s08", "s09"));
    assertThat(r.round2()).extracting(Pick::agent).containsExactly(Agent.A, Agent.B);
    assertThat(budget.used()).isEqualTo(5);
  }

  // U7-05
  @Test
  void given_disjointVotes_when_run_then_closesF3WithFill() throws AnnException {
    FakeGateExecutor ex =
        f2Setup()
            .respond(Agent.A, 2, envelope("a", "votes", "s03"))
            .respond(Agent.B, 2, envelope("b", "votes", "s04"));

    GateResult r = run(ex, rounds(), new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.F3);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s06", "s04");
    assertThat(r.round2().get(0).ids()).containsExactly("s03");
    assertThat(r.round2().get(1).ids()).containsExactly("s04");
  }

  // U7-05
  @Test
  void given_maxRoundsOne_when_run_then_skipsRoundTwoAndFills() throws AnnException {
    FakeGateExecutor ex = f2Setup();
    CallBudget budget = new CallBudget(200);

    GateResult r = run(ex, rounds(Fill.ALTERNATE_AB, 5, 1, true), budget);

    assertThat(r.closure()).isEqualTo(Closure.F3);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s06", "s04");
    assertThat(r.round2()).isEmpty();
    assertThat(ex.round2Calls()).isZero();
    assertThat(budget.used()).isEqualTo(3);
  }

  // U7-05
  @Test
  void given_emptyShortlist_when_run_then_noRoundTwo() throws AnnException {
    FakeGateExecutor ex =
        new FakeGateExecutor()
            .respond(Agent.A, 1, envelope("a", "picks", "s01", "s02", "s03"))
            .respond(Agent.B, 1, envelope("b", "picks", "s01", "s02", "s03"))
            .respond(Agent.M, 1, envelope("m", "picks", "s01", "s02", "s03"));

    GateResult r = run(ex, rounds(), new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.F3);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03");
    assertThat(ex.round2Calls()).isZero();
  }

  // U7-15
  @Test
  void given_shortlistSmallerThanVoteCount_when_run_then_votesLimitedToShortlist()
      throws AnnException {
    FakeGateExecutor ex =
        new FakeGateExecutor()
            .respond(Agent.A, 1, envelope("a", "picks", F1_A.toArray(String[]::new)))
            .respond(Agent.B, 1, envelope("b", "picks", "s01", "s02", "s03", "s04", "s06"))
            .respond(Agent.M, 1, envelope("m", "picks", "s01", "s02", "s03", "s04", "s07"))
            .respond(Agent.A, 2, envelope("a", "votes", "s07", "s06", "s05", "s01"))
            .respond(Agent.B, 2, envelope("b", "votes", "s06", "s05", "s07"));

    GateResult r = run(ex, rounds(), new CallBudget(200));

    assertThat(ex.shortlists()).containsExactly(List.of("s05", "s06", "s07"));
    assertThat(r.round2().get(0).ids()).containsExactly("s07", "s06", "s05");
    assertThat(r.round2().get(0).hallucinated()).containsExactly("s01");
    assertThat(r.closure()).isEqualTo(Closure.F2);
    assertThat(r.finalIds()).hasSize(5);
  }

  // U7-15
  @Test
  void given_voteCountSmallerThanShortlist_when_run_then_votesCutToVoteCount() throws AnnException {
    FakeGateExecutor ex =
        new FakeGateExecutor()
            .respond(Agent.A, 1, envelope("a", "picks", F1_A.toArray(String[]::new)))
            .respond(Agent.B, 1, envelope("b", "picks", "s01", "s02", "s03", "s04", "s06"))
            .respond(Agent.M, 1, envelope("m", "picks", "s01", "s02", "s03", "s04", "s07"))
            .respond(Agent.A, 2, envelope("a", "votes", "s07", "s06", "s05"))
            .respond(Agent.B, 2, envelope("b", "votes", "s06", "s07", "s05"));

    GateResult r = run(ex, rounds(Fill.ALTERNATE_AB, 2, 2, true), new CallBudget(200));

    assertThat(r.round2().get(0).ids()).containsExactly("s07", "s06");
    assertThat(r.round2().get(1).ids()).containsExactly("s06", "s07");
    assertThat(r.closure()).isEqualTo(Closure.F2);
  }

  // U7-09
  @Test
  void given_personaFailsOnce_when_run_then_retriedOnceAndClosesNormally() throws AnnException {
    FakeGateExecutor ex = f1Setup().failTimes(Agent.A, 1);
    CallBudget budget = new CallBudget(200);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.F1);
    assertThat(r.finalIds()).containsExactly("s03", "s01", "s02", "s05", "s04");
    assertThat(ex.round1Calls()).isEqualTo(2);
    assertThat(budget.used()).isEqualTo(4);
  }

  // U7-10
  @Test
  void given_personaFailsTwiceAndTwoAiAllowed_when_run_then_degradedF1Filled() throws AnnException {
    FakeGateExecutor ex =
        new FakeGateExecutor()
            .failTimes(Agent.A, 2)
            .respond(Agent.B, 1, envelope("b", "picks", F1_A.toArray(String[]::new)))
            .respond(Agent.M, 1, envelope("m", "picks", "s01", "s02", "s09", "s08", "s07"));

    GateResult r = run(ex, rounds(), new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.DEGRADED_F1);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s04", "s05");
    assertThat(r.round1().get(0).status()).isEqualTo(PickStatus.FAILED);
    assertThat(ex.round1Calls()).isEqualTo(2);
    assertThat(ex.round2Calls()).isZero();
  }

  // U7-10
  @Test
  void given_personaFailsAndTwoAiNotAllowed_when_run_then_aiUnavailable() throws AnnException {
    FakeGateExecutor ex = f1Setup().failTimes(Agent.B, 2);

    GateResult r = run(ex, rounds(Fill.ALTERNATE_AB, 5, 2, false), new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.AI_UNAVAILABLE);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s04", "s05");
  }

  // U7-10
  @Test
  void given_personaAndMediatorFail_when_run_then_aiUnavailable() throws AnnException {
    FakeGateExecutor ex = f1Setup().failTimes(Agent.B, 2).failTimes(Agent.M, 1);

    GateResult r = run(ex, rounds(), new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.AI_UNAVAILABLE);
    assertThat(r.round1()).hasSize(3);
  }

  // U7-11
  @Test
  void given_mediatorDown_when_run_then_pairIntersectionAndFillWithoutMediator()
      throws AnnException {
    FakeGateExecutor ex =
        new FakeGateExecutor()
            .respond(Agent.A, 1, envelope("a", "picks", F1_A.toArray(String[]::new)))
            .respond(Agent.B, 1, envelope("b", "picks", "s02", "s01", "s06", "s07", "s08"));

    GateResult r = run(ex, rounds(Fill.MEDIATOR_FIRST, 5, 2, true), new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.F3);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s06", "s04");
    assertThat(r.round1().get(2).status()).isEqualTo(PickStatus.FAILED);
    assertThat(r.round2()).extracting(Pick::status).containsOnly(PickStatus.FAILED);
    assertThat(ex.round1Calls()).isEqualTo(1);
  }

  // U7-11
  @Test
  void given_mediatorDownAndPairReachesThreshold_when_run_then_closesF1OnPair()
      throws AnnException {
    FakeGateExecutor ex =
        new FakeGateExecutor()
            .respond(Agent.A, 1, envelope("a", "picks", F1_A.toArray(String[]::new)))
            .respond(Agent.B, 1, envelope("b", "picks", "s02", "s01", "s06", "s07", "s08"));
    RoundsParams rp =
        new RoundsParams(
            5, 5, 5, Intersection.TRIPLE, 2, Fill.ALTERNATE_AB, RankAggregation.RANK_SUM, 2, true);

    GateResult r = run(ex, rp, new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.F1);
    assertThat(r.finalIds()).containsExactly("s01", "s02");
  }

  // U7-12
  @Test
  void given_bothPersonasFail_when_run_then_aiUnavailableWithTopOfSample() throws AnnException {
    FakeGateExecutor ex = f1Setup().failTimes(Agent.A, 2).failTimes(Agent.B, 2);
    CallBudget budget = new CallBudget(200);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.AI_UNAVAILABLE);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s04", "s05");
    assertThat(r.remaining()).containsExactly("s10", "s09", "s08", "s07", "s06");
    assertThat(r.rankSums()).containsEntry("s01", 2);
    assertThat(r.reasons().get("s01")).containsOnlyKeys(Agent.M);
    assertThat(ex.round1Calls()).isEqualTo(2);
    assertThat(budget.used()).isEqualTo(5);
  }

  // U7-12
  @Test
  void given_exhaustedBudget_when_run_then_aiUnavailableWithoutCallingExecutor()
      throws AnnException {
    FakeGateExecutor ex = f1Setup();

    GateResult r = run(ex, rounds(), new CallBudget(0));

    assertThat(r.closure()).isEqualTo(Closure.AI_UNAVAILABLE);
    assertThat(r.round1()).isEmpty();
    assertThat(ex.round1Calls()).isZero();
  }

  // U7-09
  @Test
  void given_noBudgetForRetry_when_run_then_noRetryAndDegrades() throws AnnException {
    FakeGateExecutor ex = f1Setup().failTimes(Agent.A, 1);

    GateResult r = run(ex, rounds(), new CallBudget(3));

    assertThat(ex.round1Calls()).isEqualTo(1);
    assertThat(r.closure()).isEqualTo(Closure.DEGRADED_F1);
  }

  // U7-05
  @Test
  void given_noBudgetForRoundTwo_when_run_then_fillsWithoutVoting() throws AnnException {
    FakeGateExecutor ex = f2Setup();

    GateResult r = run(ex, rounds(), new CallBudget(3));

    assertThat(ex.round2Calls()).isZero();
    assertThat(r.closure()).isEqualTo(Closure.F3);
  }
}
