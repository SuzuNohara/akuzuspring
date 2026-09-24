package com.nexus.nexussync.ann;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Result of one arkannie run: the parsed {@code .output/<runId>.md} plus the process streams.
 *
 * <p>{@link OutputReader} builds it with empty streams and zero elapsed time; {@link
 * ArkannieRunner} completes it with the data of the {@link LaunchResult} through {@link
 * #withLaunch}.
 *
 * @param runId identifier passed to arkannie as {@code --id}
 * @param status run status from the front matter of the output file
 * @param envelopes agent results indexed by dispatch id ({@code a}, {@code b}, {@code m})
 * @param stdout standard output of the arkannie process, empty when read from disk only
 * @param stderr standard error of the arkannie process, empty when read from disk only
 * @param elapsed wall-clock time of the process, {@link Duration#ZERO} when read from disk only
 */
public record RunOutput(
    String runId,
    String status,
    Map<String, Envelope> envelopes,
    String stdout,
    String stderr,
    Duration elapsed) {

  /**
   * Validates the components and copies the envelopes so the record is immutable.
   *
   * @implNote O(n) time and space, n = number of envelopes.
   */
  public RunOutput {
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(stdout, "stdout");
    Objects.requireNonNull(stderr, "stderr");
    Objects.requireNonNull(elapsed, "elapsed");
    envelopes = Collections.unmodifiableMap(new LinkedHashMap<>(envelopes));
  }

  /**
   * Returns a copy of this output completed with the streams and elapsed time of the process.
   *
   * @param processStdout standard output captured by the launcher, never {@code null}
   * @param processStderr standard error captured by the launcher, never {@code null}
   * @param processElapsed wall-clock time measured by the launcher, never {@code null}
   * @return a new output with the same run data and the given process data
   * @implNote O(n) time and space, n = number of envelopes (defensive copy).
   */
  public RunOutput withLaunch(String processStdout, String processStderr, Duration processElapsed) {
    return new RunOutput(runId, status, envelopes, processStdout, processStderr, processElapsed);
  }
}
