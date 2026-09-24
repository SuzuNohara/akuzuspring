package com.nexus.nexussync.ann;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.params.ExecutorKind;
import com.nexus.nexussync.params.RuntimeParams;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnnCheckTest {

  @TempDir Path home;

  @AfterEach
  void clearInterrupt() {
    // Leaves the test thread clean after the interruption test.
    Thread.interrupted();
  }

  private RuntimeParams params() {
    return new RuntimeParams(
        ExecutorKind.ARKANNIE, home, home.resolve("bin/arkannie"), Optional.empty(), 200, "0.3.0");
  }

  private static FakeLauncher exiting(int code, String stdout) {
    return new FakeLauncher()
        .onLaunch(cmd -> LaunchResult.of(code, stdout, "", false, Duration.ZERO));
  }

  // U6-10
  @Test
  void givenValidProgram_whenCheck_thenTrueWithCheckCommand() {
    Path program = home.resolve("round1.ann");
    FakeLauncher fake = exiting(0, "");

    assertThat(AnnCheck.check(program, params(), fake)).isTrue();
    assertThat(fake.lastCmd())
        .containsExactly(home.resolve("bin/arkannie").toString(), "--check", program.toString());
    assertThat(fake.lastDir()).contains(home);
    assertThat(fake.lastEnv()).isEqualTo(Map.of("ARKANNIE_HOME", home.toString()));
  }

  // U6-10
  @Test
  void givenInvalidProgram_whenCheck_thenFalse() {
    assertThat(AnnCheck.check(home.resolve("p.ann"), params(), exiting(1, ""))).isFalse();
  }

  @Test
  void givenTimedOutCheck_whenCheck_thenFalse() {
    FakeLauncher fake =
        new FakeLauncher().onLaunch(cmd -> LaunchResult.of(0, "", "", true, Duration.ZERO));

    assertThat(AnnCheck.check(home.resolve("p.ann"), params(), fake)).isFalse();
  }

  @Test
  void givenLauncherIoFailure_whenCheck_thenFalse() {
    ProcessLauncher failing =
        (cmd, dir, env, timeout) -> {
          throw new IOException("no binary");
        };

    assertThat(AnnCheck.check(home.resolve("p.ann"), params(), failing)).isFalse();
  }

  // U6-10
  @Test
  void givenSameVersion_whenVersionMismatch_thenEmpty() {
    FakeLauncher fake = exiting(0, "arkannie 0.3.0 (Ann v0.3)\n");

    assertThat(AnnCheck.versionMismatch(params(), fake)).isEmpty();
    assertThat(fake.lastCmd())
        .containsExactly(home.resolve("bin/arkannie").toString(), "--version");
  }

  // U6-10
  @Test
  void givenOtherVersion_whenVersionMismatch_thenWarning() {
    assertThat(AnnCheck.versionMismatch(params(), exiting(0, "arkannie 0.4.1 (Ann v0.4)")))
        .contains("arkannie 0.4.1 ≠ esperado 0.3.0");
  }

  @Test
  void givenNoVersionInStdout_whenVersionMismatch_thenWarning() {
    assertThat(AnnCheck.versionMismatch(params(), exiting(0, "arkannie dev"))).isPresent();
  }

  @Test
  void givenFailingVersionCommand_whenVersionMismatch_thenWarning() {
    assertThat(AnnCheck.versionMismatch(params(), exiting(2, "arkannie 0.3.0"))).isPresent();
  }

  @Test
  void givenTimedOutVersionCommand_whenVersionMismatch_thenWarning() {
    FakeLauncher fake =
        new FakeLauncher()
            .onLaunch(cmd -> LaunchResult.of(0, "arkannie 0.3.0", "", true, Duration.ZERO));

    assertThat(AnnCheck.versionMismatch(params(), fake)).isPresent();
  }

  @Test
  void givenInterruptedLaunch_whenVersionMismatch_thenWarningAndInterruptRestored() {
    ProcessLauncher interrupted =
        (cmd, dir, env, timeout) -> {
          throw new InterruptedException("stop");
        };

    assertThat(AnnCheck.versionMismatch(params(), interrupted)).isPresent();
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }
}
