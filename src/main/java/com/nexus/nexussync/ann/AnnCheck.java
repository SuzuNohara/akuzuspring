package com.nexus.nexussync.ann;

import com.nexus.nexussync.params.RuntimeParams;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-flight checks of the arkannie binary: program syntax and pinned version (A3).
 *
 * <p>Neither check throws: a failed launch means "not valid" for {@link #check} and a warning for
 * {@link #versionMismatch}, so callers decide whether to continue.
 */
public final class AnnCheck {

  /** Maximum time given to {@code --check} and {@code --version}. */
  static final Duration CHECK_TIMEOUT = Duration.ofSeconds(30);

  private static final Logger LOG = LoggerFactory.getLogger(AnnCheck.class);
  private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");

  private AnnCheck() {}

  /**
   * Validates an Ann program without running it ({@code arkannie --check program}).
   *
   * @param program Ann program to validate, never {@code null}
   * @param p runtime parameters with the binary and the nexussync home, never {@code null}
   * @param launcher process boundary, never {@code null}
   * @return {@code true} only if the check finished in time with exit code 0
   * @implNote O(1) time and space besides the launched process.
   */
  public static boolean check(Path program, RuntimeParams p, ProcessLauncher launcher) {
    Optional<LaunchResult> result =
        launch(launcher, p, List.of(p.arkannieBin().toString(), "--check", program.toString()));
    return result.isPresent() && !result.get().timedOut() && result.get().exitCode() == 0;
  }

  /**
   * Compares {@code arkannie --version} with {@link RuntimeParams#arkannieVersion()}.
   *
   * <p>The first {@code N.N.N} of stdout is taken as the version (real format: {@code arkannie
   * 0.3.0 (Ann v0.3)}). Any mismatch or failure is logged as a warning and returned, never thrown.
   *
   * @param p runtime parameters with the binary and the expected version, never {@code null}
   * @param launcher process boundary, never {@code null}
   * @return empty if the versions match, otherwise a human readable warning
   * @implNote O(n) time and O(1) extra space, n = length of stdout.
   */
  public static Optional<String> versionMismatch(RuntimeParams p, ProcessLauncher launcher) {
    Optional<LaunchResult> result =
        launch(launcher, p, List.of(p.arkannieBin().toString(), "--version"));
    Optional<String> warning = compare(result, p.arkannieVersion());
    warning.ifPresent(w -> LOG.warn("{}", w));
    return warning;
  }

  private static Optional<String> compare(Optional<LaunchResult> result, String expected) {
    if (result.isEmpty() || result.get().timedOut() || result.get().exitCode() != 0) {
      return Optional.of("arkannie --version failed; expected " + expected);
    }
    Matcher m = VERSION.matcher(result.get().stdout());
    if (!m.find()) {
      return Optional.of("arkannie --version reported no version; expected " + expected);
    }
    String actual = m.group();
    if (actual.equals(expected)) {
      return Optional.empty();
    }
    return Optional.of("arkannie " + actual + " ≠ esperado " + expected);
  }

  private static Optional<LaunchResult> launch(
      ProcessLauncher launcher, RuntimeParams p, List<String> cmd) {
    Map<String, String> env = Map.of(ArkannieRunner.HOME_ENV, p.nexussyncDir().toString());
    try {
      return Optional.of(launcher.launch(cmd, p.nexussyncDir(), env, CHECK_TIMEOUT));
    } catch (IOException e) {
      LOG.warn("arkannie could not be launched: {}", cmd, e);
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("interrupted while launching arkannie: {}", cmd, e);
      return Optional.empty();
    }
  }
}
