package com.nexus.nexussync.cli;

import static com.nexus.nexussync.cli.CliFixture.BASELINE;
import static com.nexus.nexussync.cli.CliFixture.COUPLE;
import static com.nexus.nexussync.cli.CliFixture.commands;
import static com.nexus.nexussync.cli.CliFixture.launcher;
import static com.nexus.nexussync.cli.CliFixture.params;
import static com.nexus.nexussync.cli.CliFixture.sweepArgs;
import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.ann.FakeLauncher;
import com.nexus.nexussync.ann.RunLock;
import com.nexus.nexussync.rounds.FakeGateExecutor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests of {@code Commands.sweep} and of the argument dispatch in {@code Commands.execute}. */
class SweepExecuteCommandTest {

  @TempDir Path tmp;

  // U13-05
  @Test
  void given_matrix_when_sweep_with_oracle_then_report_files_and_no_launch() throws Exception {
    Path home = new CliFixture(tmp).sweepHome();
    FakeLauncher launcher = launcher("", 0);

    int rc =
        commands(launcher, GateExecutorFactory.defaults(launcher))
            .sweep(sweepArgs(home, List.of(COUPLE)));

    assertThat(rc).isEqualTo(Commands.OK);
    assertThat(launcher.launches()).isZero();
    assertThat(home.resolve("params/cal-t-sampler-eps0-0.15.yml")).isRegularFile();
    assertThat(home.resolve("params/cal-t-sampler-eps0-0.5.yml")).isRegularFile();
    Path report = home.resolve("calibration/report-2026-09-24-t.md");
    assertThat(Files.readString(report, StandardCharsets.UTF_8))
        .contains(
            "cal-t-sampler-eps0-0.15", "cal-t-sampler-eps0-0.5", "## Ganador", "## Descartes");
    try (Stream<Path> records = Files.walk(home.resolve("runs/sweep"))) {
      assertThat(records.filter(f -> f.endsWith("record.json")).count()).isEqualTo(4);
    }
    assertThat(home.resolve(RunLock.LOCK_FILE)).doesNotExist();
  }

  @Test
  void given_parsed_sweep_when_execute_then_dispatched() throws Exception {
    Path home = new CliFixture(tmp).sweepHome();
    String[] args = {
      "sweep",
      "--dir",
      home.toString(),
      "--matrix",
      home.resolve("calibration/matrix-t.yml").toString(),
      "--couples",
      COUPLE,
      "--seeds",
      "1",
      "--executor",
      "oracle"
    };
    FakeLauncher launcher = launcher("", 0);

    int rc =
        commands(launcher, GateExecutorFactory.defaults(launcher)).execute(ArgParser.parse(args));

    assertThat(rc).isEqualTo(Commands.OK);
  }

  @Test
  void given_missing_thresholds_or_unknown_couple_when_sweep_then_one() throws Exception {
    Path home = new CliFixture(tmp).sweepHome();
    FakeGateExecutor fake = new FakeGateExecutor();
    Commands commands = commands(launcher("", 0), (p, cat) -> fake);

    assertThat(commands.sweep(sweepArgs(home, List.of("no-existe")))).isEqualTo(Commands.ERROR);
    Files.delete(home.resolve("calibration/thresholds.yml"));
    assertThat(commands.sweep(sweepArgs(home, List.of(COUPLE)))).isEqualTo(Commands.ERROR);
    assertThat(fake.round1Calls()).isZero();
  }

  // U11-08
  @Test
  void given_usage_error_when_execute_then_two() {
    Commands commands = commands(launcher("", 0), (p, cat) -> new FakeGateExecutor());

    assertThat(commands.execute(ArgParser.parse(new String[] {"bogus"}))).isEqualTo(Commands.USAGE);
    assertThat(commands.execute(Optional.of("not arguments"))).isEqualTo(Commands.USAGE);
  }

  @Test
  void given_parsed_arguments_when_execute_then_dispatched() throws Exception {
    Path home = new CliFixture(tmp).home();
    Commands commands = commands(launcher("arkannie 0.3.0", 0), (p, cat) -> new FakeGateExecutor());
    String params = params(home, BASELINE).toString();
    String dir = home.toString();

    assertThat(commands.execute(ArgParser.parse(new String[] {"validate", "--params", params})))
        .isEqualTo(Commands.OK);
    assertThat(commands.execute(ArgParser.parse(new String[] {"run", "--params", params})))
        .isEqualTo(Commands.USAGE);
    assertThat(
            commands.execute(
                ArgParser.parse(
                    new String[] {"replay", "--params", params, "--dir", dir, "--from", dir})))
        .isEqualTo(Commands.ERROR);
    assertThat(
            commands.execute(
                ArgParser.parse(new String[] {"compare", "--dir", dir, "--exps", BASELINE})))
        .isEqualTo(Commands.OK);
  }
}
