package com.nexus.nexussync.rounds;

import static com.nexus.nexussync.rounds.FakeGateExecutor.envelope;
import static com.nexus.nexussync.rounds.GateTest.f1Setup;
import static com.nexus.nexussync.rounds.GateTest.f2Setup;
import static com.nexus.nexussync.rounds.GateTest.rounds;
import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.params.RoundsParams;
import com.nexus.nexussync.sampler.Sample;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Gate under a call budget (A2, D-26, D-28): exhaustion, retries and non-billable executors. */
class GateBudgetTest {

  private static final Context CTX = GateTest.context();

  private final Gate gate = new Gate();

  @TempDir Path runDir;

  private GateResult run(FakeGateExecutor ex, RoundsParams rp, CallBudget budget)
      throws AnnException {
    return gate.run(GateTest.sample(), CTX, GateTest.params(rp), ex, runDir, budget);
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

  // U7-13
  @Test
  void given_budgetExhaustedBeforeRoundOne_when_run_then_aiUnavailableWithoutCalls()
      throws AnnException {
    FakeGateExecutor ex = f1Setup();
    CallBudget budget = new CallBudget(2);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.AI_UNAVAILABLE);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s04", "s05");
    assertThat(ex.round1Calls()).isZero();
    assertThat(budget.used()).isZero();
    assertThat(ex.round2Calls()).isZero();
  }

  // U7-13
  @Test
  void given_budgetExhaustedBeforeRoundTwo_when_run_then_f3FromRoundOneWithoutVotes()
      throws AnnException {
    FakeGateExecutor ex = f2Setup();
    CallBudget budget = new CallBudget(4);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.F3);
    assertThat(r.round2()).isEmpty();
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s06", "s04");
    assertThat(ex.round2Calls()).isZero();
  }

  // D-28
  @Test
  void given_oneOrTwoUnitsBeforeRoundOne_when_run_then_nothingConsumedAndNoCalls()
      throws AnnException {
    for (int units : List.of(1, 2)) {
      FakeGateExecutor ex = f1Setup();
      CallBudget budget = new CallBudget(units);

      GateResult r = run(ex, rounds(), budget);

      assertThat(r.closure()).as("units %d", units).isEqualTo(Closure.AI_UNAVAILABLE);
      assertThat(budget.used()).as("units %d", units).isZero();
      assertThat(ex.round1Calls()).as("units %d", units).isZero();
    }
  }

  // D-28
  @Test
  void given_oneUnitBeforeRoundTwo_when_run_then_unitKeptAndNoVotes() throws AnnException {
    FakeGateExecutor ex = f2Setup();
    CallBudget budget = new CallBudget(4);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.F3);
    assertThat(budget.used()).isEqualTo(3);
    assertThat(ex.round2Calls()).isZero();
  }

  // D-28
  @Test
  void given_bothPersonasFailAndOneUnitLeft_when_run_then_noPartialRetry() throws AnnException {
    FakeGateExecutor ex = f1Setup().failTimes(Agent.A, 1).failTimes(Agent.B, 1);
    CallBudget budget = new CallBudget(4);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.AI_UNAVAILABLE);
    assertThat(ex.round1Calls()).isEqualTo(1);
    assertThat(budget.used()).isEqualTo(3);
  }

  // D-26
  @Test
  void given_nonBillableExecutorAndZeroBudget_when_run_then_closesNormallyWithoutConsuming()
      throws AnnException {
    FakeGateExecutor fake =
        f2Setup()
            .respond(Agent.A, 2, envelope("a", "votes", "s03", "s04", "s05", "s06", "s07"))
            .respond(Agent.B, 2, envelope("b", "votes", "s03", "s04", "s05", "s06", "s08"))
            .failTimes(Agent.A, 1);
    GateExecutor free = new NonBillable(fake);
    CallBudget budget = new CallBudget(0);

    GateResult r =
        gate.run(GateTest.sample(), CTX, GateTest.params(rounds()), free, runDir, budget);

    assertThat(r.closure()).isEqualTo(Closure.F2);
    assertThat(fake.round1Calls()).isEqualTo(2);
    assertThat(fake.round2Calls()).isEqualTo(1);
    assertThat(budget.used()).isZero();
    assertThat(f1Setup().billable()).isTrue();
  }

  /** Delegating executor that calls no real agent (D-26). */
  private record NonBillable(FakeGateExecutor delegate) implements GateExecutor {

    @Override
    public Map<Agent, Optional<Envelope>> round1(
        Path dir, Context ctx, Sample s, Params p, Set<Agent> agents) throws AnnException {
      return delegate.round1(dir, ctx, s, p, agents);
    }

    @Override
    public Map<Agent, Optional<Envelope>> round2(Path dir, List<String> shortlist, Params p)
        throws AnnException {
      return delegate.round2(dir, shortlist, p);
    }

    @Override
    public boolean billable() {
      return false;
    }
  }
}
