package com.nexus.nexussync.ann;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Isolation of the external process: {@link LaunchResult} truncation and the programmable fake. */
class LaunchResultTest {

  private static final int LIMIT = 65_536;
  private static final String MARK = "[truncated]";
  private static final Duration ELAPSED = Duration.ofMillis(1_234);

  // U6-07
  @Test
  void given_streamsUnderLimit_when_of_then_keptVerbatim() {
    LaunchResult result = LaunchResult.of(3, "out", "err", true, ELAPSED);

    assertThat(result.exitCode()).isEqualTo(3);
    assertThat(result.stdout()).isEqualTo("out");
    assertThat(result.stderr()).isEqualTo("err");
    assertThat(result.timedOut()).isTrue();
    assertThat(result.elapsed()).isEqualTo(ELAPSED);
  }

  // U6-07
  @Test
  void given_streamsExactlyAtLimit_when_of_then_notTruncated() {
    String exact = "a".repeat(LIMIT);

    LaunchResult result = LaunchResult.of(0, exact, exact, false, ELAPSED);

    assertThat(result.stdout()).isEqualTo(exact);
    assertThat(result.stderr()).isEqualTo(exact);
  }

  // U6-07
  @Test
  void given_stdoutOverLimit_when_of_then_truncatedTo64KibWithMarker() {
    String big = "x".repeat(LIMIT + 1);

    LaunchResult result = LaunchResult.of(0, big, "", false, ELAPSED);

    assertThat(result.stdout()).hasSize(LIMIT + MARK.length()).endsWith(MARK);
    assertThat(result.stdout().getBytes(UTF_8)).hasSize(LIMIT + MARK.length());
    assertThat(result.stderr()).isEmpty();
  }

  // U6-07
  @Test
  void given_stderrOverLimit_when_of_then_truncatedTo64KibWithMarker() {
    String big = "y".repeat(LIMIT * 2);

    LaunchResult result = LaunchResult.of(1, "", big, false, ELAPSED);

    assertThat(result.stdout()).isEmpty();
    assertThat(result.stderr()).hasSize(LIMIT + MARK.length()).startsWith("yyyy").endsWith(MARK);
  }

  // U6-07
  @Test
  void given_multibyteCharAtLimit_when_of_then_cutOnCharacterBoundary() {
    // 65 535 ASCII bytes followed by a 3-byte character straddling the 64 KiB boundary.
    String big = "z".repeat(LIMIT - 1) + "€" + "tail";

    LaunchResult result = LaunchResult.of(0, big, "", false, ELAPSED);

    assertThat(result.stdout()).isEqualTo("z".repeat(LIMIT - 1) + MARK);
    assertThat(result.stdout()).doesNotContain("�");
  }

  // U6-07
  @Test
  void given_nullStream_when_constructed_then_rejected() {
    assertThatThrownBy(() -> new LaunchResult(0, null, "", false, ELAPSED))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new LaunchResult(0, "", null, false, ELAPSED))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new LaunchResult(0, "", "", false, null))
        .isInstanceOf(NullPointerException.class);
  }

  // U6-07
  @Test
  void given_programmedFake_when_launched_then_recordsCallAndCopiesOutput(@TempDir Path tmp)
      throws IOException, InterruptedException {
    Path from = tmp.resolve("canned.md");
    Path to = tmp.resolve(".output").resolve("run-1.md");
    Files.writeString(from, "---\nid: run-1\n---\n", UTF_8);
    FakeLauncher fake =
        new FakeLauncher()
            .onLaunch(cmd -> LaunchResult.of(7, "ran " + cmd.get(0), "", false, ELAPSED))
            .copyOutput(from, to);
    List<String> cmd = List.of("arkannie", "--id=run-1", "round1.ann");
    Map<String, String> env = Map.of("ARKANNIE_HOME", tmp.toString());

    LaunchResult result = fake.launch(cmd, tmp, env, Duration.ofSeconds(9));

    assertThat(result.exitCode()).isEqualTo(7);
    assertThat(result.stdout()).isEqualTo("ran arkannie");
    assertThat(fake.lastCmd()).isEqualTo(cmd);
    assertThat(fake.lastDir()).contains(tmp);
    assertThat(fake.lastEnv()).isEqualTo(env);
    assertThat(fake.lastTimeout()).contains(Duration.ofSeconds(9));
    assertThat(fake.launches()).isEqualTo(1);
    assertThat(to).hasContent("---\nid: run-1\n---\n");
  }

  // U6-07
  @Test
  void given_unprogrammedFake_when_launched_then_returnsCleanExit(@TempDir Path tmp)
      throws IOException, InterruptedException {
    FakeLauncher fake = new FakeLauncher();

    LaunchResult result = fake.launch(List.of("bin"), tmp, Map.of(), Duration.ZERO);

    assertThat(result.exitCode()).isZero();
    assertThat(result.timedOut()).isFalse();
    assertThat(fake.lastDir()).contains(tmp);
    assertThat(fake.launches()).isEqualTo(1);
  }
}
