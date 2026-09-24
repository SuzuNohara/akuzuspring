package com.nexus.nexussync.params;

/**
 * Kind of gate executor used in a run.
 *
 * <p>Named {@code ExecutorKind} instead of the {@code Executor} of the design to avoid clashing
 * with {@link java.util.concurrent.Executor}. {@code ORACLE} is the AI-free executor of the
 * calibration pre-screening (Fase C3, {@code bench.OracleExecutor}); it never calls an agent.
 */
public enum ExecutorKind {
  ARKANNIE,
  REPLAY,
  ORACLE
}
