package com.nexus.nexussync.cli;

import static com.nexus.nexussync.cli.CliFixture.BASELINE;
import static com.nexus.nexussync.cli.CliFixture.COUPLE;
import static com.nexus.nexussync.cli.CliFixture.commands;
import static com.nexus.nexussync.cli.CliFixture.json;
import static com.nexus.nexussync.cli.CliFixture.launcher;
import static com.nexus.nexussync.cli.CliFixture.params;
import static com.nexus.nexussync.cli.CliFixture.runArgs;
import static com.nexus.nexussync.cli.CliFixture.runDir;
import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.ann.FakeLauncher;
import com.nexus.nexussync.rounds.FakeGateExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests of {@code Commands.replay} and {@code Commands.compare}. */
class ReplayCompareCommandTest {

  @TempDir Path tmp;

  private Path home() throws IOException {
    return new CliFixture(tmp).home();
  }

  // U11-08
  @Test
  void given_recorded_run_when_replay_then_zero_same_final_list_and_no_launch() throws Exception {
    Path home = home();
    commands(launcher("", 0), (p, cat) -> new FakeGateExecutor().echoSample())
        .run(runArgs(home, List.of(COUPLE), OptionalLong.empty()));
    Path recorded = runDir(home.resolve("runs").resolve(BASELINE));
    FakeLauncher launcher = launcher("", 0);

    int rc =
        commands(launcher, GateExecutorFactory.defaults(launcher))
            .replay(new ReplayArgs(params(home, BASELINE), Optional.of(home), recorded));

    assertThat(rc).isEqualTo(Commands.OK);
    Path replayed = runDir(home.resolve("runs").resolve("replay").resolve(BASELINE));
    assertThat(json(replayed.resolve("gate.json")).get("finalIds"))
        .isEqualTo(json(recorded.resolve("gate.json")).get("finalIds"));
    assertThat(launcher.launches()).isZero();
  }

  @Test
  void given_from_not_a_directory_when_replay_then_one() throws Exception {
    Path home = home();

    int rc =
        commands(launcher("", 0), (p, cat) -> new FakeGateExecutor())
            .replay(new ReplayArgs(params(home, BASELINE), Optional.of(home), tmp.resolve("nope")));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  @Test
  void given_unknown_couple_id_when_replay_then_one() throws Exception {
    Path home = home();
    Path from = Files.createDirectories(tmp.resolve("runs/x/999-1000/run"));

    int rc =
        commands(launcher("", 0), (p, cat) -> new FakeGateExecutor())
            .replay(new ReplayArgs(params(home, BASELINE), Optional.of(home), from));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  @Test
  void given_experiments_when_compare_then_dated_report() throws Exception {
    Path home = home();
    commands(launcher("", 0), (p, cat) -> new FakeGateExecutor().echoSample())
        .run(runArgs(home, List.of(COUPLE), OptionalLong.empty()));

    int rc =
        commands(launcher("", 0), (p, cat) -> new FakeGateExecutor())
            .compare(new CompareArgs(home, List.of(BASELINE, "missing")));

    assertThat(rc).isEqualTo(Commands.OK);
    Path report = home.resolve("reports/compare-2026-09-24.md");
    assertThat(report).isRegularFile();
    assertThat(Files.readString(report, StandardCharsets.UTF_8)).contains(BASELINE, "missing");
  }

  @Test
  void given_no_experiments_when_compare_then_usage() {
    int rc =
        commands(launcher("", 0), (p, cat) -> new FakeGateExecutor())
            .compare(new CompareArgs(tmp, List.of()));

    assertThat(rc).isEqualTo(Commands.USAGE);
  }

  @Test
  void given_bad_experiment_name_when_compare_then_one() {
    int rc =
        commands(launcher("", 0), (p, cat) -> new FakeGateExecutor())
            .compare(new CompareArgs(tmp, List.of("../etc")));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  @Test
  void given_reports_is_a_file_when_compare_then_one() throws Exception {
    Files.writeString(tmp.resolve(Commands.REPORTS), "x", StandardCharsets.UTF_8);

    int rc =
        commands(launcher("", 0), (p, cat) -> new FakeGateExecutor())
            .compare(new CompareArgs(tmp, List.of("exp")));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }
}
