package com.nexus.nexussync.rounds;

import static com.nexus.nexussync.rounds.FakeGateExecutor.envelope;
import static com.nexus.nexussync.rounds.GateTest.F1_A;
import static com.nexus.nexussync.rounds.GateTest.f1Setup;
import static com.nexus.nexussync.rounds.GateTest.rounds;
import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.params.Fill;
import com.nexus.nexussync.params.Intersection;
import com.nexus.nexussync.params.RankAggregation;
import com.nexus.nexussync.params.RoundsParams;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Gate when agents fail: retries (U7-09, D-24) and degradation (U7-10..U7-12). */
class GateDegradationTest {

  private static final Context CTX = GateTest.context();

  private final Gate gate = new Gate();

  @TempDir Path runDir;

  private GateResult run(FakeGateExecutor ex, RoundsParams rp, CallBudget budget)
      throws AnnException {
    return gate.run(GateTest.sample(), CTX, GateTest.params(rp), ex, runDir, budget);
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
    assertThat(ex.round1Agents())
        .containsExactly(Set.of(Agent.A, Agent.B, Agent.M), Set.of(Agent.A));
    assertThat(budget.used()).isEqualTo(4);
  }

  // D-24
  @Test
  void given_bothPersonasFailOnce_when_run_then_retryAsksBothAndKeepsFirstMediator()
      throws AnnException {
    FakeGateExecutor ex = f1Setup().failTimes(Agent.A, 1).failTimes(Agent.B, 1);
    CallBudget budget = new CallBudget(200);

    GateResult r = run(ex, rounds(), budget);

    assertThat(ex.round1Agents())
        .containsExactly(Set.of(Agent.A, Agent.B, Agent.M), Set.of(Agent.A, Agent.B));
    assertThat(budget.used()).isEqualTo(5);
    assertThat(r.closure()).isEqualTo(Closure.F1);
    assertThat(r.round1().get(2).status()).isEqualTo(PickStatus.OK);
    assertThat(runDir.resolve("round1-2.ann")).content().doesNotContain("--id=m");
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
}
