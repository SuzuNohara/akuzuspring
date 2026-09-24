package com.nexus.nexussync.rounds;

import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.sampler.Sample;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Dispatches the agents of each round of the gate (unit U7). */
public interface GateExecutor {

  /**
   * Runs round one: both personas pick and the mediator recommends.
   *
   * @param runDir directory of the run
   * @param ctx couple context
   * @param s sample offered to the agents
   * @param p parameters of the experiment
   * @return one entry per agent; {@code Optional.empty()} when the agent produced no envelope
   * @throws AnnException if the program could not be rendered, launched or read
   * @implNote Cost dominated by the agent calls.
   */
  Map<Agent, Optional<Envelope>> round1(Path runDir, Context ctx, Sample s, Params p)
      throws AnnException;

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
}
