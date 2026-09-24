package com.nexus.nexussync.params;

/**
 * Kind of gate executor used in a run.
 *
 * <p>Named {@code ExecutorKind} instead of the {@code Executor} of the design to avoid clashing
 * with {@link java.util.concurrent.Executor}.
 */
public enum ExecutorKind {
  ARKANNIE,
  REPLAY
}
