package com.nexus.nexussync.ann;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Programmable {@link ProcessLauncher} for tests: never starts a process.
 *
 * <p>The result is produced by the function given to {@link #onLaunch(Function)} (a clean exit with
 * empty streams by default). Every call records the command, directory, environment and timeout it
 * received, and {@link #copyOutput(Path, Path)} optionally copies a canned file on launch to
 * simulate the {@code .output/<runId>.md} that arkannie would write.
 */
public final class FakeLauncher implements ProcessLauncher {

  private Function<List<String>, LaunchResult> behaviour =
      cmd -> LaunchResult.of(0, "", "", false, Duration.ZERO);
  private Optional<Path> copyFrom = Optional.empty();
  private Optional<Path> copyTo = Optional.empty();
  private List<String> lastCmd = List.of();
  private Optional<Path> lastDir = Optional.empty();
  private Map<String, String> lastEnv = Map.of();
  private Optional<Duration> lastTimeout = Optional.empty();
  private int launches;

  /**
   * Programs the result returned on launch as a function of the command.
   *
   * @param onLaunch maps the launched command to the result to return, never {@code null}
   * @return this fake, for chaining
   * @implNote O(1) time and space.
   */
  public FakeLauncher onLaunch(Function<List<String>, LaunchResult> onLaunch) {
    this.behaviour = Objects.requireNonNull(onLaunch, "onLaunch");
    return this;
  }

  /**
   * Makes every launch copy {@code from} to {@code to} (creating parent directories) before
   * returning, simulating the output file arkannie writes.
   *
   * @param from existing file to copy, never {@code null}
   * @param to destination path, overwritten if present, never {@code null}
   * @return this fake, for chaining
   * @implNote O(1) time and space; the copy itself is O(size of {@code from}) per launch.
   */
  public FakeLauncher copyOutput(Path from, Path to) {
    this.copyFrom = Optional.of(from);
    this.copyTo = Optional.of(to);
    return this;
  }

  /**
   * Records the call, performs the programmed copy and returns the programmed result.
   *
   * @implNote O(1) time and space plus the optional file copy.
   */
  @Override
  public LaunchResult launch(List<String> cmd, Path dir, Map<String, String> env, Duration timeout)
      throws IOException {
    lastCmd = List.copyOf(cmd);
    lastDir = Optional.of(dir);
    lastEnv = Map.copyOf(env);
    lastTimeout = Optional.of(timeout);
    launches++;
    if (copyFrom.isPresent() && copyTo.isPresent()) {
      Path target = copyTo.get();
      Path parent = target.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.copy(copyFrom.get(), target, StandardCopyOption.REPLACE_EXISTING);
    }
    return behaviour.apply(lastCmd);
  }

  /**
   * Returns the command of the last launch, empty if none happened.
   *
   * @return an immutable copy of the last command
   * @implNote O(1) time and space.
   */
  public List<String> lastCmd() {
    return lastCmd;
  }

  /**
   * Returns the working directory of the last launch.
   *
   * @return the directory, or empty if no launch happened
   * @implNote O(1) time and space.
   */
  public Optional<Path> lastDir() {
    return lastDir;
  }

  /**
   * Returns the environment of the last launch, empty if none happened.
   *
   * @return an immutable copy of the last environment
   * @implNote O(1) time and space.
   */
  public Map<String, String> lastEnv() {
    return lastEnv;
  }

  /**
   * Returns the timeout of the last launch.
   *
   * @return the timeout, or empty if no launch happened
   * @implNote O(1) time and space.
   */
  public Optional<Duration> lastTimeout() {
    return lastTimeout;
  }

  /**
   * Returns how many times {@link #launch} was called.
   *
   * @return the number of launches so far
   * @implNote O(1) time and space.
   */
  public int launches() {
    return launches;
  }
}
