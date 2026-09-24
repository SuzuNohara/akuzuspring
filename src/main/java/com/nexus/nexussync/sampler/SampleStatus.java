package com.nexus.nexussync.sampler;

/** Outcome of the sampler (unit U4). */
public enum SampleStatus {
  /** The sample reached the minimum size. */
  OK,
  /** Not enough eligible activities even after every allowed relaxation. */
  INSUFFICIENT_SAMPLE,
  /** The couple has no shared window. */
  NO_WINDOW
}
