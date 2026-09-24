package com.nexus.nexussync.cli;

import static com.nexus.nexussync.cli.CliFixture.BASELINE;
import static com.nexus.nexussync.cli.CliFixture.catalog;
import static com.nexus.nexussync.cli.CliFixture.commands;
import static com.nexus.nexussync.cli.CliFixture.delete;
import static com.nexus.nexussync.cli.CliFixture.launcher;
import static com.nexus.nexussync.cli.CliFixture.params;
import static com.nexus.nexussync.cli.CliFixture.validateArgs;
import static com.nexus.nexussync.cli.CliFixture.withRuntime;
import static com.nexus.nexussync.cli.CliFixture.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.ann.FakeLauncher;
import com.nexus.nexussync.bench.OracleExecutor;
import com.nexus.nexussync.bench.ReplayExecutor;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.params.ExecutorKind;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.rounds.ArkannieExecutor;
import com.nexus.nexussync.rounds.FakeGateExecutor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests of {@code Commands.validate}, parameter loading and the default executor factory. */
class ValidateCommandTest {

  @TempDir Path tmp;

  private Path home() throws IOException {
    return new CliFixture(tmp).home();
  }

  // U11-07
  @Test
  void given_pinned_arkannie_when_validate_then_zero_and_agents_rendered() throws Exception {
    Path home = home();
    FakeLauncher launcher = launcher("arkannie 0.3.0 (Ann v0.3)", 0);

    int rc =
        commands(launcher, (p, cat) -> new FakeGateExecutor())
            .validate(validateArgs(home, BASELINE));

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
        commands(launcher("arkannie 0.3.0", 0), (p, cat) -> new FakeGateExecutor())
            .validate(validateArgs(home, "bad"));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  // U11-07
  @Test
  void given_other_arkannie_version_when_validate_then_zero_with_warning() throws Exception {
    Path home = home();

    int rc =
        commands(launcher("arkannie 0.2.9 (Ann v0.2)", 0), (p, cat) -> new FakeGateExecutor())
            .validate(validateArgs(home, BASELINE));

    assertThat(rc).isEqualTo(Commands.OK);
  }

  @Test
  void given_check_rejects_program_when_validate_then_one() throws Exception {
    Path home = home();

    int rc =
        commands(launcher("arkannie 0.3.0", 1), (p, cat) -> new FakeGateExecutor())
            .validate(validateArgs(home, BASELINE));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  @Test
  void given_missing_agent_when_validate_then_one() throws Exception {
    Path home = home();
    delete(home.resolve(".agents/mediador"));

    int rc =
        commands(launcher("arkannie 0.3.0", 0), (p, cat) -> new FakeGateExecutor())
            .validate(validateArgs(home, BASELINE));

    assertThat(rc).isEqualTo(Commands.ERROR);
  }

  @Test
  void given_default_factory_when_create_then_executor_per_kind() throws Exception {
    Path home = home();
    Params p = Commands.load(params(home, BASELINE), Optional.of(home));
    Catalog cat = catalog(home, p);
    GateExecutorFactory factory = GateExecutorFactory.defaults(launcher("", 0));

    assertThat(factory.create(p, cat)).isInstanceOf(ArkannieExecutor.class);
    assertThat(factory.create(withRuntime(p, ExecutorKind.REPLAY, Optional.of(home)), cat))
        .isInstanceOf(ReplayExecutor.class);
    assertThat(factory.create(withRuntime(p, ExecutorKind.ORACLE, Optional.empty()), cat))
        .isInstanceOf(OracleExecutor.class);
    assertThatThrownBy(
            () -> factory.create(withRuntime(p, ExecutorKind.REPLAY, Optional.empty()), cat))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void given_oracle_executor_in_yaml_when_load_then_accepted_and_oracle_built() throws Exception {
    Path home = home();
    write(home.resolve("params/oracle.yml"), "experiment: oracle\nruntime:\n  executor: ORACLE\n");

    Params p = Commands.load(params(home, "oracle"), Optional.of(home));

    assertThat(p.runtime().executor()).isEqualTo(ExecutorKind.ORACLE);
    assertThat(GateExecutorFactory.defaults(launcher("", 0)).create(p, catalog(home, p)))
        .isInstanceOf(OracleExecutor.class);
  }

  @Test
  void given_dir_when_load_then_runtime_resolved_against_it() throws Exception {
    Path home = home();

    Params p = Commands.load(params(home, BASELINE), Optional.of(home));

    assertThat(p.runtime().nexussyncDir()).isEqualTo(home.toAbsolutePath().normalize());
    assertThat(p.runtime().arkannieBin()).isEqualTo(home.resolve("arkannie/bin/arkannie"));
    assertThat(p.catalogDir()).isEqualTo(Path.of("fixtures/catalog-synthetic"));
  }
}
