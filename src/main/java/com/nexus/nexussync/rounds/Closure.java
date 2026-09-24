package com.nexus.nexussync.rounds;

/** How the gate produced its final list (unit U7). */
public enum Closure {
  /** Round-one intersection reached the threshold. */
  F1,
  /** Round-one intersection plus the shared round-two votes reached the threshold. */
  F2,
  /** The list was completed with the fill policy. */
  F3,
  /** One persona failed; intersection of the surviving persona and the mediator. */
  DEGRADED_F1,
  /** Not enough agents (or budget) to run the gate; top of the sample instead. */
  AI_UNAVAILABLE
}
