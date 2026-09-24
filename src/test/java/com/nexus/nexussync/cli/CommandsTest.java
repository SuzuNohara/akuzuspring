package com.nexus.nexussync.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.nexussync.NexussyncException;
import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.ann.FakeLauncher;
import com.nexus.nexussync.ann.LaunchResult;
import com.nexus.nexussync.ann.RunLock;
import com.nexus.nexussync.bench.ReplayExecutor;
import com.nexus.nexussync.params.ExecutorKind;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.params.RuntimeParams;
import com.nexus.nexussync.rounds.ArkannieExecutor;
import com.nexus.nexussync.rounds.FakeGateExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests of the CLI commands; a temporary nexussync home, a fake launcher, no agent calls. */
class CommandsTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC);
  private static final String BASELINE = "baseline-haiku";
  private static final String COUPLE = "opuestos";
  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir Path tmp;

  // U11-07
  @Test
  void given_pinned_arkannie_when_validate_then_zero_and_agents_rendered() throws Exception {
    Path home = home();
    FakeLauncher launcher = launcher("arkannie 0.3.0 (Ann v0.3)", 0);

    int rc = commands(launcher, p -> new FakeGateExecutor()).validate(validateArgs(home, BASELINE));

    assertThat(rc).isEqualTo(Commands.OK);
    assertThat(home.resolve(".agents/persona/agent.yaml")).isRegularFile();
    assertThat(home.resolve(".agents/mediador/agent.yaml")).isRegularFile();
    assertThat(launcher.launches()).isEqualTo(3);
    assertThat(launcher.lastCmd()).contains("--check");
    assertThat(launcher.lastCmd().get(0))
        .isEqualTo(home.resolve("arkannie/bin/arkannie").toString());
  }

  // U11-07
  @Test
  void given_out_of_range_params_when_validate_then_one() throws Exception {
    Path home = home();
    write(home.resolve("params/bad.yml"), "experiment: bad\nsampler:\n  eta: 99.0\n");

    int rc =
        commands(launcher("arkannie 0.3.0", 0), p -> new FakeGateExecutor())
            .validate(validateArgs(home, "bad"));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  // U11-07
  @Test
  void given_other_arkannie_version_when_validate_then_zero_with_warning() throws Exception {
    Path home = home();

    int rc =
        commands(launcher("arkannie 0.2.9 (Ann v0.2)", 0), p -> new FakeGateExecutor())
            .validate(validateArgs(home, BASELINE));

    assertThat(rc).isEqualTo(Commands.OK);
  }

  @Test
  void given_check_rejects_program_when_validate_then_one() throws Exception {
    Path home = home();

    int rc =
        commands(launcher("arkannie 0.3.0", 1), p -> new FakeGateExecutor())
            .validate(validateArgs(home, BASELINE));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  @Test
  void given_missing_agent_when_validate_then_one() throws Exception {
    Path home = home();
    delete(home.resolve(".agents/mediador"));

    int rc =
        commands(launcher("arkannie 0.3.0", 0), p -> new FakeGateExecutor())
            .validate(validateArgs(home, BASELINE));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  // U11-08
  @Test
  void given_fixtures_when_run_then_zero_and_trace_files() throws Exception {
    Path home = home();

    int rc =
        commands(launcher("", 0), p -> new FakeGateExecutor().echoSample())
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
  void given_recorded_run_when_replay_then_zero_same_final_list_and_no_launch() throws Exception {
    Path home = home();
    commands(launcher("", 0), p -> new FakeGateExecutor().echoSample())
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

  // U11-08
  @Test
  void given_no_couples_when_run_then_usage() throws Exception {
    Path home = home();

    int rc =
        commands(launcher("", 0), p -> new FakeGateExecutor())
            .run(runArgs(home, List.of(), OptionalLong.empty()));

    assertThat(rc).isEqualTo(Commands.USAGE);
  }

  // U11-08
  @Test
  void given_unknown_couple_when_run_then_one() throws Exception {
    Path home = home();
    FakeGateExecutor fake = new FakeGateExecutor();

    int rc =
        commands(launcher("", 0), p -> fake)
            .run(runArgs(home, List.of(COUPLE, "no-existe"), OptionalLong.empty()));

    assertThat(rc).isEqualTo(Commands.ERROR);
    assertThat(fake.round1Calls()).isZero();
  }

  @Test
  void given_path_traversal_slug_when_run_then_one() throws Exception {
    Path home = home();

    int rc =
        commands(launcher("", 0), p -> new FakeGateExecutor())
            .run(runArgs(home, List.of("../couples/opuestos"), OptionalLong.empty()));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  // U11-08
  @Test
  void given_lock_held_when_run_then_one() throws Exception {
    Path home = home();
    Files.createFile(home.resolve(RunLock.LOCK_FILE));

    int rc =
        commands(launcher("", 0), p -> new FakeGateExecutor().echoSample())
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
        commands(launcher("", 0), p -> new FakeGateExecutor().echoSample())
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
  void given_from_not_a_directory_when_replay_then_one() throws Exception {
    Path home = home();

    int rc =
        commands(launcher("", 0), p -> new FakeGateExecutor())
            .replay(new ReplayArgs(params(home, BASELINE), Optional.of(home), tmp.resolve("nope")));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  @Test
  void given_unknown_couple_id_when_replay_then_one() throws Exception {
    Path home = home();
    Path from = Files.createDirectories(tmp.resolve("runs/x/999-1000/run"));

    int rc =
        commands(launcher("", 0), p -> new FakeGateExecutor())
            .replay(new ReplayArgs(params(home, BASELINE), Optional.of(home), from));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  @Test
  void given_experiments_when_compare_then_dated_report() throws Exception {
    Path home = home();
    commands(launcher("", 0), p -> new FakeGateExecutor().echoSample())
        .run(runArgs(home, List.of(COUPLE), OptionalLong.empty()));

    int rc =
        commands(launcher("", 0), p -> new FakeGateExecutor())
            .compare(new CompareArgs(home, List.of(BASELINE, "missing")));

    assertThat(rc).isEqualTo(Commands.OK);
    Path report = home.resolve("reports/compare-2026-09-24.md");
    assertThat(report).isRegularFile();
    assertThat(Files.readString(report, StandardCharsets.UTF_8)).contains(BASELINE, "missing");
  }

  @Test
  void given_no_experiments_when_compare_then_usage() {
    int rc =
        commands(launcher("", 0), p -> new FakeGateExecutor())
            .compare(new CompareArgs(tmp, List.of()));

    assertThat(rc).isEqualTo(Commands.USAGE);
  }

  @Test
  void given_bad_experiment_name_when_compare_then_one() {
    int rc =
        commands(launcher("", 0), p -> new FakeGateExecutor())
            .compare(new CompareArgs(tmp, List.of("../etc")));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  @Test
  void given_reports_is_a_file_when_compare_then_one() throws Exception {
    Files.writeString(tmp.resolve(Commands.REPORTS), "x", StandardCharsets.UTF_8);

    int rc =
        commands(launcher("", 0), p -> new FakeGateExecutor())
            .compare(new CompareArgs(tmp, List.of("exp")));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  // U11-08
  @Test
  void given_usage_error_when_execute_then_two() {
    Commands commands = commands(launcher("", 0), p -> new FakeGateExecutor());

    assertThat(commands.execute(ArgParser.parse(new String[] {"bogus"}))).isEqualTo(Commands.USAGE);
    assertThat(commands.execute(Optional.of("not arguments"))).isEqualTo(Commands.USAGE);
  }

  @Test
  void given_parsed_arguments_when_execute_then_dispatched() throws Exception {
    Path home = home();
    Commands commands = commands(launcher("arkannie 0.3.0", 0), p -> new FakeGateExecutor());
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

  @Test
  void given_default_factory_when_create_then_executor_per_kind() throws Exception {
    Path home = home();
    Params p = Commands.load(params(home, BASELINE), Optional.of(home));
    GateExecutorFactory factory = GateExecutorFactory.defaults(launcher("", 0));

    assertThat(factory.create(p)).isInstanceOf(ArkannieExecutor.class);
    assertThat(factory.create(withRuntime(p, ExecutorKind.REPLAY, Optional.of(home))))
        .isInstanceOf(ReplayExecutor.class);
    assertThatThrownBy(() -> factory.create(withRuntime(p, ExecutorKind.REPLAY, Optional.empty())))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void given_dir_when_load_then_runtime_resolved_against_it() throws Exception {
    Path home = home();

    Params p = Commands.load(params(home, BASELINE), Optional.of(home));

    assertThat(p.runtime().nexussyncDir()).isEqualTo(home.toAbsolutePath().normalize());
    assertThat(p.runtime().arkannieBin()).isEqualTo(home.resolve("arkannie/bin/arkannie"));
    assertThat(p.catalogDir()).isEqualTo(Path.of("fixtures/catalog-synthetic"));
  }

  @Test
  void given_rounds_below_one_when_run_args_then_rejected() {
    assertThatThrownBy(
            () -> new RunArgs(tmp, Optional.empty(), List.of(COUPLE), 0, OptionalLong.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** Commands whose every budget is recorded in {@code budgets}. */
  private static Commands recording(FakeGateExecutor fake, List<CallBudget> budgets) {
    return new Commands(
        launcher("", 0),
        p -> fake,
        CLOCK,
        max -> {
          CallBudget b = new CallBudget(max);
          budgets.add(b);
          return b;
        },
        Random::new);
  }

  private Commands commands(FakeLauncher launcher, GateExecutorFactory factory) {
    return new Commands(launcher, factory, CLOCK, CallBudget::new, Random::new);
  }

  /** {@code --version} answers {@code version}; every launch exits with {@code rc}. */
  private static FakeLauncher launcher(String version, int rc) {
    return new FakeLauncher()
        .onLaunch(
            cmd ->
                LaunchResult.of(
                    rc, cmd.contains("--version") ? version : "", "", false, Duration.ZERO));
  }

  /**
   * Temporary nexussync home: copies of the params and agents, links to the real fixtures and Ann
   * templates; runs, lock and reports land in the copy.
   */
  private Path home() throws IOException {
    Path nx = nexussyncDir();
    Path home = Files.createDirectories(tmp.resolve("nx"));
    Files.createDirectories(home.resolve("params"));
    for (String f : List.of("default.yml", BASELINE + ".yml")) {
      Files.copy(nx.resolve("params").resolve(f), home.resolve("params").resolve(f));
    }
    for (String agent : List.of("persona", "mediador")) {
      Path to = Files.createDirectories(home.resolve(".agents").resolve(agent));
      for (String f : List.of("agent.yaml.tmpl", "harness.md")) {
        Files.copy(nx.resolve(".agents").resolve(agent).resolve(f), to.resolve(f));
      }
    }
    Files.createSymbolicLink(home.resolve("fixtures"), nx.resolve("fixtures").toAbsolutePath());
    Files.createSymbolicLink(home.resolve("ann"), nx.resolve("ann").toAbsolutePath());
    return home;
  }

  private static Path nexussyncDir() {
    String dir = System.getProperty("nexussync.dir", "");
    Path root = Path.of(dir.isEmpty() ? "missing-nexussync-dir" : dir);
    Assumptions.assumeTrue(
        Files.isDirectory(root.resolve("fixtures")), "nexussync.dir no definido o sin fixtures");
    return root;
  }

  private static Path params(Path home, String name) {
    return home.resolve("params").resolve(name + ".yml");
  }

  private static ValidateArgs validateArgs(Path home, String name) {
    return new ValidateArgs(params(home, name), Optional.of(home));
  }

  private static RunArgs runArgs(Path home, List<String> couples, OptionalLong seed) {
    return runArgs(home, BASELINE, couples, seed);
  }

  private static RunArgs runArgs(Path home, String name, List<String> couples, OptionalLong seed) {
    return new RunArgs(params(home, name), Optional.of(home), couples, 1, seed);
  }

  /** The only run directory under an experiment directory. */
  private static Path runDir(Path expDir) throws IOException {
    try (Stream<Path> walk = Files.walk(expDir)) {
      List<Path> records =
          walk.filter(f -> f.getFileName().toString().equals("record.json")).toList();
      assertThat(records).hasSize(1);
      return records.get(0).getParent();
    }
  }

  private static JsonNode json(Path file) throws IOException {
    return JSON.readTree(file.toFile());
  }

  private static void write(Path file, String content) throws IOException {
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }

  private static void delete(Path dir) throws IOException {
    try (Stream<Path> walk = Files.walk(dir)) {
      for (Path f : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(f);
      }
    }
  }

  private static Params withRuntime(Params p, ExecutorKind kind, Optional<Path> replayDir) {
    RuntimeParams rt = p.runtime();
    RuntimeParams moved =
        new RuntimeParams(
            kind,
            rt.nexussyncDir(),
            rt.arkannieBin(),
            replayDir,
            rt.maxCalls(),
            rt.arkannieVersion());
    return new Params(
        p.experiment(),
        p.seed(),
        p.catalogDir(),
        p.placesCsv(),
        p.sampler(),
        p.rounds(),
        p.agents(),
        p.decision(),
        p.place(),
        p.learning(),
        p.context(),
        moved,
        p.rubric(),
        p.bench());
  }
}
