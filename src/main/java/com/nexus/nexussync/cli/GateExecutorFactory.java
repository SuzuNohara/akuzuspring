package com.nexus.nexussync.cli;

import com.nexus.nexussync.ann.ArkannieRunner;
import com.nexus.nexussync.ann.ProcessLauncher;
import com.nexus.nexussync.bench.OracleExecutor;
import com.nexus.nexussync.bench.ReplayExecutor;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.rounds.ArkannieExecutor;
import com.nexus.nexussync.rounds.GateExecutor;
import java.util.Objects;

/**
 * Builds the {@link GateExecutor} of a run from its parameters (unit U11, A6).
 *
 * <p>The CLI asks for one executor per couple, so a {@link ReplayExecutor} never shares its attempt
 * counters between couples. Tests inject a factory returning a fake executor.
 */
@FunctionalInterface
public interface GateExecutorFactory {

  /**
   * Creates the executor for a run with {@code p}.
   *
   * @param p parameters of the run, with the runtime already resolved
   * @param cat catalog of the run (used by the {@code ORACLE} executor)
   * @return a fresh executor
   * @throws IllegalStateException if the parameters cannot produce an executor (REPLAY without
   *     {@code runtime.replay_dir})
   */
  GateExecutor create(Params p, Catalog cat);

  /**
   * Default factory: {@link ArkannieExecutor} over {@code runtime.nexussync_dir} for {@code
   * ARKANNIE}, {@link ReplayExecutor} over {@code runtime.replay_dir} for {@code REPLAY} and {@link
   * OracleExecutor} over the catalog and {@code sampler} parameters for {@code ORACLE}. The switch
   * is exhaustive without {@code default}: a new kind never falls silently to arkannie.
   *
   * @param launcher process boundary used by arkannie, never {@code null}
   * @return the factory
   * @implNote O(1) time and space per created executor.
   */
  static GateExecutorFactory defaults(ProcessLauncher launcher) {
    Objects.requireNonNull(launcher, "launcher");
    return (p, cat) -> byKind(launcher, p, cat);
  }

  private static GateExecutor byKind(ProcessLauncher launcher, Params p, Catalog cat) {
    return switch (p.runtime().executor()) {
      case ARKANNIE ->
          new ArkannieExecutor(new ArkannieRunner(launcher), p.runtime().nexussyncDir());
      case REPLAY ->
          new ReplayExecutor(
              p.runtime()
                  .replayDir()
                  .orElseThrow(
                      () -> new IllegalStateException("REPLAY without runtime.replay_dir")));
      case ORACLE -> new OracleExecutor(cat, p.sampler());
    };
  }
}
