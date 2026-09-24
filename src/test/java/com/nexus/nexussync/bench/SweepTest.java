package com.nexus.nexussync.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.Intersection;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.params.ParamsException;
import com.nexus.nexussync.params.ParamsLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests of the sweep generator and its report (U13-05). */
class SweepTest {

  private static final String HEAD = "stage: 1-sampler\nbase: default.yml\nmode: one_at_a_time\n";
  private static final LocalDate DATE = LocalDate.of(2026, 9, 24);
  private static final Thresholds TH = new Thresholds(0.0, 0.15, 0.70, 0.02, 0.40, 0.50, 0.60);

  @TempDir Path tmp;
  private Path paramsDir;

  @BeforeEach
  void setUp() throws IOException {
    String dir = System.getProperty("nexussync.dir", "");
    Path nx = Path.of(dir.isEmpty() ? "missing-nexussync-dir" : dir);
    Path defaults = nx.resolve("params").resolve("default.yml");
    Assumptions.assumeTrue(Files.isRegularFile(defaults), "nexussync.dir no definido");
    paramsDir = Files.createDirectories(tmp.resolve("nx").resolve("params"));
    Files.copy(defaults, paramsDir.resolve("default.yml"));
  }

  // U13-05
  @Test
  void given_matrix_when_expand_then_one_valid_file_per_value_with_its_override() throws Exception {
    Path matrix =
        matrix(
            HEAD
                + "params:\n"
                + "  sampler.eps0: [0.15, 0.50]\n"
                + "  sampler.weights_init:\n"
                + "    - {label: interest, value: {INTEREST: 2.0, PRICE: 1.0, DISTANCE: 1.0,"
                + " NOVELTY: 1.0, EMOTION: 1.0, COLLAB: 1.0, SEASON: 1.0}}\n"
                + "  rounds.intersection: [PAIR_MEDIATOR_TIEBREAK]\n"
                + "  learning.eta_pos_neg:\n"
                + "    - {label: 0.30-0.30,"
                + " set: {learning.eta_pos: 0.30, learning.eta_neg: 0.30}}\n");

    List<Path> files = Sweep.expand(matrix, paramsDir);

    assertThat(files)
        .extracting(f -> f.getFileName().toString())
        .containsExactly(
            "cal-1-sampler-sampler-eps0-0.15.yml",
            "cal-1-sampler-sampler-eps0-0.5.yml",
            "cal-1-sampler-sampler-weights-init-interest.yml",
            "cal-1-sampler-rounds-intersection-pair-mediator-tiebreak.yml",
            "cal-1-sampler-learning-eta-pos-neg-0.30-0.30.yml");
    Params eps = load(files.get(0));
    assertThat(eps.experiment()).isEqualTo("cal-1-sampler-sampler-eps0-0.15");
    assertThat(eps.sampler().eps0()).isEqualTo(0.15);
    assertThat(load(files.get(2)).sampler().weightsInit()).containsEntry(Feature.INTEREST, 2.0);
    assertThat(load(files.get(3)).rounds().intersection())
        .isEqualTo(Intersection.PAIR_MEDIATOR_TIEBREAK);
    Params pair = load(files.get(4));
    assertThat(pair.learning().etaPos()).isEqualTo(0.30);
    assertThat(pair.learning().etaNeg()).isEqualTo(0.30);
    assertThat(Files.readString(files.get(0), StandardCharsets.UTF_8))
        .startsWith("# Generado por Sweep")
        .doesNotContain("eta_pos");
  }

  // U13-05
  @Test
  void given_same_matrix_when_expanded_twice_then_identical_files() throws Exception {
    Path matrix = matrix(HEAD + "params:\n  sampler.max_per_type: [2, 3]\n");

    List<String> first = contents(Sweep.expand(matrix, paramsDir));
    List<String> second = contents(Sweep.expand(matrix, paramsDir));

    assertThat(second).isEqualTo(first);
  }

  // U13-05
  @Test
  void given_out_of_range_value_when_expand_then_params_exception_and_nothing_written()
      throws Exception {
    Path matrix = matrix(HEAD + "params:\n  sampler.eps0: [0.15, 1.5]\n");

    assertThatThrownBy(() -> Sweep.expand(matrix, paramsDir))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("sampler.eps0");
    assertThat(generated()).isEmpty();
  }

  // U13-05
  @Test
  void given_colliding_values_when_expand_then_params_exception() throws Exception {
    Path matrix = matrix(HEAD + "params:\n  sampler.eps0: [0.5, 0.50]\n");

    assertThatThrownBy(() -> Sweep.expand(matrix, paramsDir))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("duplicate");
  }

  // U13-05
  @Test
  void given_malformed_matrices_when_expand_then_params_exception() throws Exception {
    List<String> invalid =
        List.of(
            HEAD + "params:\n  sampler.eps0: [0.1]\nextra: 1\n",
            HEAD.replace("one_at_a_time", "grid") + "params:\n  sampler.eps0: [0.1]\n",
            HEAD.replace("1-sampler", "Etapa 1") + "params:\n  sampler.eps0: [0.1]\n",
            HEAD.replace("default.yml", "../x.yml") + "params:\n  sampler.eps0: [0.1]\n",
            HEAD.replace("default.yml", "absent.yml") + "params:\n  sampler.eps0: [0.1]\n",
            HEAD + "params: {}\n",
            HEAD + "params:\n  sampler.eps0: 0.1\n",
            HEAD + "params:\n  sampler.eps0: []\n",
            HEAD + "params:\n  Sampler.Eps0: [0.1]\n",
            HEAD + "params:\n  sampler.eps0: [null]\n",
            HEAD + "params:\n  sampler.eps0: [{value: 0.1}]\n",
            HEAD + "params:\n  sampler.eps0: [{label: a, value: 0.1, set: {sampler.eps0: 0.1}}]\n",
            HEAD + "params:\n  sampler.eps0: [{label: a}]\n",
            HEAD + "params:\n  sampler.eps0: [{label: a, value: 0.1, extra: 1}]\n",
            HEAD + "params:\n  sampler.eps0: [{label: 1, value: 0.1}]\n",
            HEAD.replace("default.yml", "[a]") + "params:\n  sampler.eps0: [0.1]\n",
            HEAD.replace("1-sampler", "[1]") + "params:\n  sampler.eps0: [0.1]\n",
            HEAD + "params:\n  sampler..eps0: [0.1]\n",
            HEAD + "params:\n  sampler.eps0: [[0.1]]\n",
            HEAD + "params:\n  sampler.eps0: [\"A B\"]\n",
            HEAD + "params:\n  pair: [{label: a, set: [1]}]\n",
            HEAD + "params:\n  pair: [{label: a, set: {}}]\n",
            HEAD + "params:\n  pair: [{label: a, set: {Bad.Path: 1}}]\n",
            HEAD + "params:\n  experiment.x: [1]\n",
            HEAD + "params:\n  sampler.unknown_key: [1]\n",
            "- a\n- list\n",
            "stage: [unclosed\n");
    for (int i = 0; i < invalid.size(); i++) {
      Path m = matrix(invalid.get(i));
      assertThatThrownBy(() -> Sweep.expand(m, paramsDir))
          .as("case %d", i)
          .isInstanceOf(ParamsException.class);
    }
    assertThatThrownBy(() -> Sweep.expand(tmp.resolve("absent.yml"), paramsDir))
        .isInstanceOf(ParamsException.class);
  }

  @Test
  void given_other_base_when_expand_then_base_values_kept_and_renamed() throws Exception {
    Files.writeString(
        paramsDir.resolve("cal-winner.yml"),
        "experiment: cal-winner\nseed: 9\nsampler:\n  eps0: 0.2\n",
        StandardCharsets.UTF_8);
    Path matrix =
        matrix(
            "stage: 2-rounds\nbase: cal-winner.yml\nmode: one_at_a_time\n"
                + "params:\n  rounds.k_pick: [10]\n");

    Params p = load(Sweep.expand(matrix, paramsDir).get(0));

    assertThat(p.experiment()).isEqualTo("cal-2-rounds-rounds-k-pick-10");
    assertThat(p.seed()).isEqualTo(9);
    assertThat(p.sampler().eps0()).isEqualTo(0.2);
    assertThat(p.rounds().pickCount()).isEqualTo(10);
  }

  @Test
  void given_unwritable_params_dir_when_expand_then_params_exception() throws Exception {
    Path matrix = matrix(HEAD + "params:\n  sampler.eps0: [0.2]\n");
    Files.createDirectories(paramsDir.resolve("cal-1-sampler-sampler-eps0-0.2.yml"));

    assertThatThrownBy(() -> Sweep.expand(matrix, paramsDir))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("cannot write");
  }

  @Test
  void given_matrix_when_stage_then_read() throws Exception {
    assertThat(Sweep.stage(matrix(HEAD + "params:\n  sampler.eps0: [0.2]\n")))
        .isEqualTo("1-sampler");
  }

  @Test
  void given_gold_dir_when_gold_then_loaded_or_empty_when_missing() throws Exception {
    assertThat(Sweep.gold(tmp.resolve("no-gold"))).isEmpty();
    assertThat(Sweep.gold(GoldSetTest.resource("gold"))).isNotEmpty();
  }

  @Test
  void given_runner_when_run_then_every_file_and_seed_run_and_report_written() throws Exception {
    Path nx = paramsDir.getParent();
    Path cal = Files.createDirectories(nx.resolve("calibration"));
    Files.copy(GoldSetTest.resource("thresholds.yml"), cal.resolve("thresholds.yml"));
    Path matrix = matrix(HEAD + "params:\n  sampler.eps0: [0.15, 0.35]\n");
    List<String> calls = new ArrayList<>();

    Path report =
        Sweep.run(
            matrix,
            nx,
            List.of(1L, 2L),
            DATE,
            (file, seed) -> {
              calls.add(file.getFileName() + "@" + seed);
              return List.of();
            });

    assertThat(calls)
        .containsExactly(
            "cal-1-sampler-sampler-eps0-0.15.yml@1",
            "cal-1-sampler-sampler-eps0-0.15.yml@2",
            "cal-1-sampler-sampler-eps0-0.35.yml@1",
            "cal-1-sampler-sampler-eps0-0.35.yml@2");
    assertThat(report).isEqualTo(cal.resolve("report-2026-09-24-1-sampler.md"));
    assertThat(Files.readString(report, StandardCharsets.UTF_8))
        .contains("| cal-1-sampler-sampler-eps0-0.15 | 0 |", "ninguno: todas descartadas");
  }

  @Test
  void given_results_when_report_then_table_winner_ranking_and_discards() throws Exception {
    ExperimentResult best = result("best", 0.0, 0.1, 0.9);
    ExperimentResult next = result("next", 0.0, 0.1, 0.8);
    ExperimentResult bad = result("bad", 0.5, 0.1, 1.0);

    Path report =
        Sweep.report(tmp.resolve("cal"), "3-learning", DATE, List.of(bad, next, best), TH);

    assertThat(Files.readString(report, StandardCharsets.UTF_8))
        .contains(
            "# Calibración — etapa 3-learning — 2026-09-24",
            "| best | 3 |",
            "`best`",
            "1. `best`",
            "2. `next`",
            "- `bad`: GOLD_VIOLATION = 0.5000 > 0.0000",
            "n/a");
    assertThat(
            Files.readString(
                Sweep.report(tmp.resolve("cal"), "x", DATE, List.of(best), TH),
                StandardCharsets.UTF_8))
        .contains("## Descartes\n\nninguno");
  }

  @Test
  void given_unwritable_calibration_dir_when_report_then_params_exception() throws Exception {
    Path file = Files.writeString(tmp.resolve("cal-file"), "x", StandardCharsets.UTF_8);

    assertThatThrownBy(() -> Sweep.report(file, "x", DATE, List.of(), TH))
        .isInstanceOf(ParamsException.class);
  }

  private Path matrix(String yaml) throws IOException {
    return Files.writeString(tmp.resolve("matrix.yml"), yaml, StandardCharsets.UTF_8);
  }

  private Params load(Path file) throws ParamsException {
    return ParamsLoader.load(file, paramsDir.resolve("default.yml"));
  }

  private List<Path> generated() throws IOException {
    try (Stream<Path> files = Files.list(paramsDir)) {
      return files.filter(f -> f.getFileName().toString().startsWith(Sweep.PREFIX)).toList();
    }
  }

  private static List<String> contents(List<Path> files) throws IOException {
    List<String> out = new ArrayList<>();
    for (Path f : files) {
      out.add(Files.readString(f, StandardCharsets.UTF_8));
    }
    return out;
  }

  private static ExperimentResult result(String name, double violation, double gap, double hit) {
    Map<String, Double> m = new LinkedHashMap<>();
    m.put(Metrics.GOLD_VIOLATION, violation);
    m.put(Metrics.FAIRNESS_GAP, gap);
    m.put(Metrics.GOLD_HIT, hit);
    m.put(Metrics.CALLS_PER_RUN, 0.0);
    return new ExperimentResult(name, 3, m);
  }
}
