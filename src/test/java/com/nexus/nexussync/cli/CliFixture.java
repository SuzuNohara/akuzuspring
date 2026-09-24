package com.nexus.nexussync.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.ann.FakeLauncher;
import com.nexus.nexussync.ann.LaunchResult;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.CatalogLoader;
import com.nexus.nexussync.params.ExecutorKind;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.params.RuntimeParams;
import com.nexus.nexussync.rounds.FakeGateExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;

/**
 * Shared fixture of the CLI command tests: a temporary nexussync home built from the real params,
 * agents, fixtures and Ann templates, a fake launcher and a fixed clock. No agent is ever called.
 */
final class CliFixture {

  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC);
  static final String BASELINE = "baseline-haiku";
  static final String COUPLE = "opuestos";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final Path tmp;

  CliFixture(Path tmp) {
    this.tmp = tmp;
  }

  /**
   * Temporary nexussync home: copies of the params and agents, links to the real fixtures and Ann
   * templates; runs, lock and reports land in the copy.
   */
  Path home() throws IOException {
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

  /** {@link #home()} plus thresholds and a two-value matrix {@code calibration/matrix-t.yml}. */
  Path sweepHome() throws IOException {
    Path home = home();
    Path cal = Files.createDirectories(home.resolve("calibration"));
    write(
        cal.resolve("thresholds.yml"),
        "gold_violation_rate_max: 0.0\nfairness_gap_max: 0.15\ngold_hit_rate_min: 0.70\n"
            + "hallucination_rate_max: 0.02\nstability_at_seed_min: 0.40\n"
            + "closure_f1_rate_min: 0.50\nchosen_in_top3_truth_rate_min: 0.60\n");
    write(
        cal.resolve("matrix-t.yml"),
        "stage: t\nbase: default.yml\nmode: one_at_a_time\nparams:\n"
            + "  sampler.eps0: [0.15, 0.50]\n");
    return home;
  }

  static SweepArgs sweepArgs(Path home, List<String> couples) {
    return new SweepArgs(
        home,
        home.resolve("calibration/matrix-t.yml"),
        couples,
        List.of(1L, 2L),
        ExecutorKind.ORACLE,
        1);
  }

  static Catalog catalog(Path home, Params p) throws Exception {
    return CatalogLoader.load(home.resolve(p.catalogDir()), home.resolve(p.placesCsv()));
  }

  /** Commands whose every budget is recorded in {@code budgets}. */
  static Commands recording(FakeGateExecutor fake, List<CallBudget> budgets) {
    return new Commands(
        launcher("", 0),
        (p, cat) -> fake,
        CLOCK,
        max -> {
          CallBudget b = new CallBudget(max);
          budgets.add(b);
          return b;
        },
        Random::new);
  }

  static Commands commands(FakeLauncher launcher, GateExecutorFactory factory) {
    return new Commands(launcher, factory, CLOCK, CallBudget::new, Random::new);
  }

  /** {@code --version} answers {@code version}; every launch exits with {@code rc}. */
  static FakeLauncher launcher(String version, int rc) {
    return new FakeLauncher()
        .onLaunch(
            cmd ->
                LaunchResult.of(
                    rc, cmd.contains("--version") ? version : "", "", false, Duration.ZERO));
  }

  private static Path nexussyncDir() {
    String dir = System.getProperty("nexussync.dir", "");
    Path root = Path.of(dir.isEmpty() ? "missing-nexussync-dir" : dir);
    Assumptions.assumeTrue(
        Files.isDirectory(root.resolve("fixtures")), "nexussync.dir no definido o sin fixtures");
    return root;
  }

  static Path params(Path home, String name) {
    return home.resolve("params").resolve(name + ".yml");
  }

  static ValidateArgs validateArgs(Path home, String name) {
    return new ValidateArgs(params(home, name), Optional.of(home));
  }

  static RunArgs runArgs(Path home, List<String> couples, OptionalLong seed) {
    return runArgs(home, BASELINE, couples, seed);
  }

  static RunArgs runArgs(Path home, String name, List<String> couples, OptionalLong seed) {
    return new RunArgs(params(home, name), Optional.of(home), couples, 1, seed);
  }

  /** The only run directory under an experiment directory. */
  static Path runDir(Path expDir) throws IOException {
    try (Stream<Path> walk = Files.walk(expDir)) {
      List<Path> records =
          walk.filter(f -> f.getFileName().toString().equals("record.json")).toList();
      assertThat(records).hasSize(1);
      return records.get(0).getParent();
    }
  }

  static JsonNode json(Path file) throws IOException {
    return JSON.readTree(file.toFile());
  }

  static void write(Path file, String content) throws IOException {
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }

  static void delete(Path dir) throws IOException {
    try (Stream<Path> walk = Files.walk(dir)) {
      for (Path f : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(f);
      }
    }
  }

  static Params withRuntime(Params p, ExecutorKind kind, Optional<Path> replayDir) {
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
