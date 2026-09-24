package com.nexus.nexussync.ann;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.params.ExecutorKind;
import com.nexus.nexussync.params.RuntimeParams;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArkannieRunnerTest {

  private static final String RUN_ID = "base-c1-1758672000000-42";
  private static final Duration TIMEOUT = Duration.ofSeconds(90);

  @TempDir Path home;

  @AfterEach
  void clearInterrupt() {
    // Leaves the test thread clean after the interruption test.
    Thread.interrupted();
  }

  private RuntimeParams params() {
    return new RuntimeParams(
        ExecutorKind.ARKANNIE,
        home,
        home.resolve("arkannie/bin/arkannie"),
        Optional.empty(),
        200,
        "0.3.0");
  }

  private static Path fixture(String name) throws URISyntaxException {
    URL url =
        Objects.requireNonNull(
            ArkannieRunnerTest.class.getResource("/nexussync/output/" + name), name);
    return Path.of(url.toURI());
  }

  private Path outputFile() {
    return home.resolve(".output").resolve(RUN_ID + ".md");
  }

  // U6-05
  @Test
  void givenCleanExit_whenRun_thenExactCommandDirEnvAndCompletedOutput() throws Exception {
    Path program = home.resolve("runs/round1.ann");
    FakeLauncher fake =
        new FakeLauncher()
            .copyOutput(fixture("ok.md"), outputFile())
            .onLaunch(cmd -> LaunchResult.of(0, "out", "err", false, Duration.ofSeconds(8)));

    RunOutput out = new ArkannieRunner(fake).run(program, RUN_ID, params(), TIMEOUT);

    assertThat(fake.lastCmd())
        .containsExactly(
            home.resolve("arkannie/bin/arkannie").toString(), "--id=" + RUN_ID, program.toString());
    assertThat(fake.lastDir()).contains(home);
    assertThat(fake.lastEnv()).isEqualTo(Map.of("ARKANNIE_HOME", home.toString()));
    assertThat(fake.lastTimeout()).contains(TIMEOUT);
    assertThat(out.envelopes()).containsOnlyKeys("a", "b", "m");
    assertThat(out.status()).isEqualTo("success");
    assertThat(out.stdout()).isEqualTo("out");
    assertThat(out.stderr()).isEqualTo("err");
    assertThat(out.elapsed()).isEqualTo(Duration.ofSeconds(8));
  }

  // U6-06
  @Test
  void givenTimedOut_whenRun_thenTimeoutWithDuration() {
    FakeLauncher fake =
        new FakeLauncher().onLaunch(cmd -> LaunchResult.of(-1, "", "", true, TIMEOUT));

    assertKind(fake, AnnException.Kind.TIMEOUT, TIMEOUT.toString());
  }

  // U6-06
  @Test
  void givenNonZeroExit_whenRun_thenExitWithStderr() {
    FakeLauncher fake =
        new FakeLauncher()
            .onLaunch(cmd -> LaunchResult.of(3, "", "boom: bad agent", false, Duration.ZERO));

    assertKind(fake, AnnException.Kind.EXIT, "boom: bad agent");
  }

  // U6-06
  @Test
  void givenCleanExitWithoutOutput_whenRun_thenNotFound() {
    assertKind(new FakeLauncher(), AnnException.Kind.NOT_FOUND, RUN_ID);
  }

  @Test
  void givenLauncherIoFailure_whenRun_thenExitWithCause() {
    ProcessLauncher failing =
        (cmd, dir, env, timeout) -> {
          throw new IOException("no such binary");
        };

    assertThatThrownBy(
            () -> new ArkannieRunner(failing).run(home.resolve("p.ann"), RUN_ID, params(), TIMEOUT))
        .isInstanceOfSatisfying(
            AnnException.class, e -> assertThat(e.kind()).isEqualTo(AnnException.Kind.EXIT))
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void givenLauncherInterrupted_whenRun_thenExitAndInterruptRestored() {
    ProcessLauncher interrupted =
        (cmd, dir, env, timeout) -> {
          throw new InterruptedException("stop");
        };

    assertThatThrownBy(
            () ->
                new ArkannieRunner(interrupted)
                    .run(home.resolve("p.ann"), RUN_ID, params(), TIMEOUT))
        .isInstanceOfSatisfying(
            AnnException.class, e -> assertThat(e.kind()).isEqualTo(AnnException.Kind.EXIT))
        .hasCauseInstanceOf(InterruptedException.class);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  private void assertKind(FakeLauncher fake, AnnException.Kind kind, String inMessage) {
    assertThatThrownBy(
            () -> new ArkannieRunner(fake).run(home.resolve("p.ann"), RUN_ID, params(), TIMEOUT))
        .isInstanceOfSatisfying(AnnException.class, e -> assertThat(e.kind()).isEqualTo(kind))
        .hasMessageContaining(inMessage);
    assertThat(fake.launches()).isEqualTo(1);
  }
}
