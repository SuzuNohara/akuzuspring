package com.nexus.nexussync.ann;

import com.nexus.nexussync.params.RuntimeParams;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs one Ann program with the arkannie binary and reads its output (unit U6).
 *
 * <p>The command is {@code [bin, "--id=" + runId, program]}, launched in {@code nexussyncDir} with
 * {@code ARKANNIE_HOME=nexussyncDir}, so arkannie only sees the agents of the nexussync home.
 */
public final class ArkannieRunner {

  /** Environment variable that points arkannie at its home. */
  static final String HOME_ENV = "ARKANNIE_HOME";

  private final ProcessLauncher launcher;

  /**
   * Creates a runner that launches arkannie through the given launcher.
   *
   * @param launcher process boundary, never {@code null}
   * @implNote O(1) time and space.
   */
  public ArkannieRunner(ProcessLauncher launcher) {
    this.launcher = Objects.requireNonNull(launcher, "launcher");
  }

  /**
   * Launches arkannie on {@code program} and returns the parsed output completed with the process
   * streams and elapsed time.
   *
   * @param program rendered Ann program to run, never {@code null}
   * @param runId identifier passed as {@code --id}; names {@code .output/<runId>.md}
   * @param p runtime parameters with the binary and the nexussync home, never {@code null}
   * @param timeout maximum wall-clock time of the process, never {@code null}
   * @return the run output with {@code stdout}, {@code stderr} and {@code elapsed} of the launch
   * @throws AnnException {@code TIMEOUT} if the process timed out; {@code EXIT} if it exited with a
   *     non-zero code or could not be launched; {@code NOT_FOUND}/{@code PARSE} from {@link
   *     OutputReader#read}
   * @implNote O(n) time and space, n = size of the output file and captured streams.
   */
  public RunOutput run(Path program, String runId, RuntimeParams p, Duration timeout)
      throws AnnException {
    List<String> cmd = List.of(p.arkannieBin().toString(), "--id=" + runId, program.toString());
    Map<String, String> env = Map.of(HOME_ENV, p.nexussyncDir().toString());
    LaunchResult result = launch(cmd, p.nexussyncDir(), env, timeout);
    if (result.timedOut()) {
      throw new AnnException(
          AnnException.Kind.TIMEOUT, "arkannie run " + runId + " timed out after " + timeout);
    }
    if (result.exitCode() != 0) {
      throw new AnnException(
          AnnException.Kind.EXIT,
          "arkannie run " + runId + " exited with " + result.exitCode() + ": " + result.stderr());
    }
    return OutputReader.read(p.nexussyncDir(), runId)
        .withLaunch(result.stdout(), result.stderr(), result.elapsed());
  }

  private LaunchResult launch(List<String> cmd, Path dir, Map<String, String> env, Duration timeout)
      throws AnnException {
    try {
      return launcher.launch(cmd, dir, env, timeout);
    } catch (IOException e) {
      throw new AnnException(AnnException.Kind.EXIT, "arkannie could not be launched", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AnnException(AnnException.Kind.EXIT, "interrupted while running arkannie", e);
    }
  }
}
