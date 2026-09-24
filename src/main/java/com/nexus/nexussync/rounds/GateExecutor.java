package com.nexus.nexussync.rounds;

import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.sampler.Sample;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Dispatches the agents of each round of the gate (unit U7). */
public interface GateExecutor {

  /**
   * Runs round one for every agent: both personas pick and the mediator recommends. Delegates to
   * {@link #round1(Path, Context, Sample, Params, Set)} with all the agents.
   *
   * @param runDir directory of the run
   * @param ctx couple context
   * @param s sample offered to the agents
   * @param p parameters of the experiment
   * @return one entry per agent; {@code Optional.empty()} when the agent produced no envelope
   * @throws AnnException if the program could not be rendered, launched or read
   * @implNote Cost dominated by the agent calls.
   */
  default Map<Agent, Optional<Envelope>> round1(Path runDir, Context ctx, Sample s, Params p)
      throws AnnException {
    return round1(runDir, ctx, s, p, EnumSet.allOf(Agent.class));
  }

  /**
   * Runs round one only for the given agents (D-24): the gate's retry asks for the failed personas
   * alone, so no other agent is dispatched again.
   *
   * @param runDir directory of the run
   * @param ctx couple context
   * @param s sample offered to the agents
   * @param p parameters of the experiment
   * @param agents dispatches to run ({@code A}, {@code B}, {@code M}); the others are not called
   * @return one entry per requested agent; {@code Optional.empty()} when it produced no envelope
   * @throws AnnException if the program could not be rendered, launched or read
   * @implNote Cost dominated by the agent calls: one per requested agent.
   */
  Map<Agent, Optional<Envelope>> round1(
      Path runDir, Context ctx, Sample s, Params p, Set<Agent> agents) throws AnnException;

  /**
   * Runs round two: both personas vote over the shortlist.
   *
   * @param runDir directory of the run
   * @param shortlist ids to vote on
   * @param p parameters of the experiment
   * @return one entry per persona; {@code Optional.empty()} when the agent produced no envelope
   * @throws AnnException if the program could not be rendered, launched or read
   * @implNote Cost dominated by the agent calls.
   */
  Map<Agent, Optional<Envelope>> round2(Path runDir, List<String> shortlist, Params p)
      throws AnnException;

  /**
   * Tells whether the dispatches of this executor are real agent calls that consume the {@code
   * CallBudget} (D-26). Executors that call no agent (the bench oracle, the replay) return {@code
   * false} and the gate then reserves nothing.
   *
   * @return {@code true} by default
   * @implNote O(1) time and space.
   */
  default boolean billable() {
    return true;
  }
}
