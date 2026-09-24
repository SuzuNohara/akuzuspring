package com.nexus.nexussync.cli;

import static com.nexus.nexussync.cli.CliFixture.BASELINE;
import static com.nexus.nexussync.cli.CliFixture.COUPLE;
import static com.nexus.nexussync.cli.CliFixture.commands;
import static com.nexus.nexussync.cli.CliFixture.json;
import static com.nexus.nexussync.cli.CliFixture.launcher;
import static com.nexus.nexussync.cli.CliFixture.recording;
import static com.nexus.nexussync.cli.CliFixture.runArgs;
import static com.nexus.nexussync.cli.CliFixture.runDir;
import static com.nexus.nexussync.cli.CliFixture.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.NexussyncException;
import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.ann.FakeLauncher;
import com.nexus.nexussync.ann.RunLock;
import com.nexus.nexussync.rounds.FakeGateExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests of {@code Commands.run} and the couple resolution it relies on. */
class RunCommandTest {

  @TempDir Path tmp;

  private Path home() throws IOException {
    return new CliFixture(tmp).home();
  }

  // U11-08
  @Test
  void given_fixtures_when_run_then_zero_and_trace_files() throws Exception {
    Path home = home();

    int rc =
        commands(launcher("", 0), (p, cat) -> new FakeGateExecutor().echoSample())
            .run(runArgs(home, List.of(COUPLE), OptionalLong.empty()));

    assertThat(rc).isEqualTo(Commands.OK);
    Path runDir = runDir(home.resolve("runs").resolve(BASELINE));
    for (String f : List.of("params.yml", "context.json", "sample.json", "gate.json")) {
      assertThat(runDir.resolve(f)).isRegularFile();
    }
    assertThat(runDir.resolve("round1.out.md")).isRegularFile();
    assertThat(home.resolve(RunLock.LOCK_FILE)).doesNotExist();
  }

  // U11-08
  @Test
  void given_no_couples_when_run_then_usage() throws Exception {
    Path home = home();

    int rc =
        commands(launcher("", 0), (p, cat) -> new FakeGateExecutor())
            .run(runArgs(home, List.of(), OptionalLong.empty()));

    assertThat(rc).isEqualTo(Commands.USAGE);
  }

  // U11-08
  @Test
  void given_unknown_couple_when_run_then_one() throws Exception {
    Path home = home();
    FakeGateExecutor fake = new FakeGateExecutor();

    int rc =
        commands(launcher("", 0), (p, cat) -> fake)
            .run(runArgs(home, List.of(COUPLE, "no-existe"), OptionalLong.empty()));

    assertThat(rc).isEqualTo(Commands.ERROR);
    assertThat(fake.round1Calls()).isZero();
  }

  @Test
  void given_path_traversal_slug_when_run_then_one() throws Exception {
    Path home = home();

    int rc =
        commands(launcher("", 0), (p, cat) -> new FakeGateExecutor())
            .run(runArgs(home, List.of("../couples/opuestos"), OptionalLong.empty()));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  // U11-08
  @Test
  void given_lock_held_when_run_then_one() throws Exception {
    Path home = home();
    Files.createFile(home.resolve(RunLock.LOCK_FILE));

    int rc =
        commands(launcher("", 0), (p, cat) -> new FakeGateExecutor().echoSample())
            .run(runArgs(home, List.of(COUPLE), OptionalLong.empty()));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  // U11-09 (D-28 of the concurrent calibration cluster: the gate reserves the three round-one
  // calls atomically, so with max_calls 1 nothing is consumed and used() is 0, not 1)
  @Test
  void given_max_calls_one_when_run_then_ai_unavailable_and_no_agent_call() throws Exception {
    Path home = home();
    write(home.resolve("params/budget.yml"), "experiment: budget\nruntime:\n  max_calls: 1\n");
    List<CallBudget> budgets = new ArrayList<>();
    FakeGateExecutor fake = new FakeGateExecutor().echoSample();

    int rc =
        recording(fake, budgets)
            .run(runArgs(home, "budget", List.of(COUPLE), OptionalLong.empty()));

    assertThat(rc).isEqualTo(Commands.OK);
    assertThat(budgets).hasSize(1);
    assertThat(budgets.get(0).used()).isZero();
    assertThat(fake.round1Calls()).isZero();
    Path runDir = runDir(home.resolve("runs").resolve("budget"));
    assertThat(json(runDir.resolve("gate.json")).get("closure").asText())
        .isEqualTo("AI_UNAVAILABLE");
  }

  // U11-09
  @Test
  void given_budget_for_one_round_when_run_two_couples_then_budget_shared() throws Exception {
    Path home = home();
    write(home.resolve("params/budget.yml"), "experiment: budget\nruntime:\n  max_calls: 3\n");
    List<CallBudget> budgets = new ArrayList<>();
    FakeGateExecutor fake = new FakeGateExecutor().echoSample();

    int rc =
        recording(fake, budgets)
            .run(runArgs(home, "budget", List.of(COUPLE, "hogar"), OptionalLong.empty()));

    assertThat(rc).isEqualTo(Commands.OK);
    assertThat(budgets).hasSize(1);
    assertThat(budgets.get(0).used()).isEqualTo(3);
    assertThat(fake.round1Calls()).isEqualTo(1);
    assertThat(fake.round2Calls()).isZero();
  }

  @Test
  void given_seed_when_run_then_record_uses_it() throws Exception {
    Path home = home();

    int rc =
        commands(launcher("", 0), (p, cat) -> new FakeGateExecutor().echoSample())
            .run(runArgs(home, List.of(COUPLE), OptionalLong.of(7L)));

    assertThat(rc).isEqualTo(Commands.OK);
    Path runDir = runDir(home.resolve("runs").resolve(BASELINE));
    assertThat(json(runDir.resolve("record.json")).get("seed").asLong()).isEqualTo(7L);
  }

  @Test
  void given_replay_executor_without_replay_dir_when_run_then_one() throws Exception {
    Path home = home();
    write(home.resolve("params/norec.yml"), "experiment: norec\nruntime:\n  executor: REPLAY\n");
    FakeLauncher launcher = launcher("", 0);

    int rc =
        commands(launcher, GateExecutorFactory.defaults(launcher))
            .run(runArgs(home, "norec", List.of(COUPLE), OptionalLong.empty()));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  @Test
  void given_all_when_couples_then_every_fixture_sorted() throws Exception {
    Path home = home();

    List<Path> all = Commands.couples(home, List.of(Commands.ALL));

    assertThat(all).hasSizeGreaterThanOrEqualTo(10);
    assertThat(all).isSorted();
    assertThat(all).contains(home.resolve("fixtures/couples/" + COUPLE + ".json"));
  }

  @Test
  void given_no_couples_dir_when_all_then_error() {
    assertThatThrownBy(() -> Commands.couples(tmp, List.of(Commands.ALL)))
        .isInstanceOf(NexussyncException.class);
  }

  @Test
  void given_rounds_below_one_when_run_args_then_rejected() {
    assertThatThrownBy(
            () -> new RunArgs(tmp, Optional.empty(), List.of(COUPLE), 0, OptionalLong.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
