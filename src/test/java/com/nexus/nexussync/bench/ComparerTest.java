package com.nexus.nexussync.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.rounds.Agent;
import com.nexus.nexussync.rounds.Closure;
import com.nexus.nexussync.rounds.Pick;
import com.nexus.nexussync.rounds.PickStatus;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComparerTest {

  private static final LocalDate DATE = LocalDate.of(2026, 9, 24);

  @TempDir Path runs;

  private static RunRecord record(Closure closure, String runId) {
    Map<Agent, Map<Feature, Double>> truth = new EnumMap<>(Agent.class);
    truth.put(Agent.A, Map.of(Feature.INTEREST, 1.0));
    Pick a = new Pick(Agent.A, List.of("x"), List.of("r"), List.of("ghost"), PickStatus.OK);
    RunRecord base =
        BenchFixtures.record(
            BenchFixtures.gate(closure, List.of("x"), List.of(a), List.of()),
            BenchFixtures.emptySample(),
            Optional.of(BenchFixtures.weights(Map.of(Feature.INTEREST, 1.0))),
            Duration.ofMillis(1500),
            new RunRecord.Evidence(truth, 1, List.of("MUSEUM"), 0, 0));
    return new RunRecord(
        runId,
        base.coupleId(),
        base.experiment(),
        base.paramsHash(),
        base.seed(),
        base.sample(),
        base.gate(),
        base.decision(),
        base.places(),
        base.before(),
        base.after(),
        base.elapsed(),
        base.evidence());
  }

  /** Writes the record as {@link CoupleRunner} does: truth redacted to its hash (D-33). */
  private static void save(Path expDir, RunRecord r) throws Exception {
    BenchIo.write(
        BenchIo.JSON,
        expDir.resolve(r.coupleId()).resolve(r.runId()).resolve(CoupleRunner.RECORD),
        TruthRedaction.record(r));
  }

  private static Map<String, Map<Agent, Map<Feature, Double>>> truthOf(RunRecord r) {
    return Map.of(r.coupleId(), r.evidence().truthWeights());
  }

  // U11-05
  @Test
  void given_twoExperiments_when_compare_then_oneColumnEachNaAndX5NoteAndDateInName()
      throws Exception {
    Path base = runs.resolve("baseline-haiku");
    save(base, record(Closure.F1, "run-1"));
    save(base, record(Closure.F3, "run-2"));
    Path empty = Files.createDirectories(runs.resolve("sin-corridas"));
    Files.createDirectories(runs.resolve("weights"));

    Path report =
        Comparer.compare(
            List.of(base, empty), runs.resolve("reports"), DATE, runs.resolve("no-couples"));

    assertThat(report).isEqualTo(runs.resolve("reports").resolve("compare-2026-09-24.md"));
    String md = Files.readString(report, StandardCharsets.UTF_8);
    assertThat(md)
        .contains("2026-09-24")
        .contains("| Métrica | baseline-haiku | sin-corridas |")
        .contains("| runs | 2 | 0 |")
        .contains("| closureF1Rate | 0.5000 | n/a |")
        .contains("| closureF3Rate | 0.5000 | n/a |")
        .contains("| hallucinationRate | 0.5000 | n/a |")
        .contains("| meanSeconds | 1.5000 | n/a |")
        .contains("| truthAlignment | n/a | n/a |")
        .contains("| hoursParsedRate | n/a | n/a |")
        .contains(Comparer.X5_NOTE);
    for (String metric : Metrics.NAMES) {
      assertThat(md).contains("| " + metric + " |");
    }
  }

  // U11-05
  @Test
  void given_recordWritten_when_readBack_then_equalRecord() throws Exception {
    RunRecord r = record(Closure.F2, "run-9");
    Path exp = runs.resolve("exp");
    save(exp, r);

    assertThat(Comparer.records(exp, truthOf(r))).containsExactly(r);
    assertThat(Comparer.records(exp)).singleElement().isNotEqualTo(r);
    assertThat(Comparer.records(exp).get(0).evidence().truthWeights()).isEmpty();
    assertThat(Comparer.records(runs.resolve("missing"))).isEmpty();
  }

  // U11-05 (D-33: the truth comes back from the fixtures only when its hash matches)
  @Test
  void given_truthRestored_when_compare_then_truthMetricsComputed() throws Exception {
    RunRecord r = record(Closure.F1, "run-1");
    Path exp = runs.resolve("exp");
    save(exp, r);
    Map<String, Map<Agent, Map<Feature, Double>>> other =
        Map.of(r.coupleId(), Map.of(Agent.A, Map.of(Feature.PRICE, 1.0)));

    List<RunRecord> restored = Comparer.records(exp, truthOf(r));
    List<RunRecord> mismatched = Comparer.records(exp, other);

    assertThat(Metrics.of(restored).get(Metrics.TRUTH_ALIGNMENT)).isEqualTo(1.0);
    assertThat(mismatched.get(0).evidence().truthWeights()).isEmpty();
    assertThat(Metrics.of(mismatched).get(Metrics.TRUTH_ALIGNMENT)).isNaN();
    assertThat(Files.readString(exp.resolve("1-2/run-1/record.json")))
        .doesNotContain("truthWeights")
        .contains(TruthRedaction.RECORD_KEY);
  }

  @Test
  void given_corruptRecord_when_compare_then_uncheckedIo() throws Exception {
    Path exp = runs.resolve("exp");
    Path file = exp.resolve("1-2").resolve("run-1").resolve(CoupleRunner.RECORD);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "{roto", StandardCharsets.UTF_8);

    assertThatThrownBy(() -> Comparer.compare(List.of(exp), runs.resolve("reports"), DATE))
        .isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void given_reportsPathIsFile_when_compare_then_uncheckedIo() throws Exception {
    Path file = Files.writeString(runs.resolve("reports"), "x", StandardCharsets.UTF_8);

    assertThatThrownBy(() -> Comparer.compare(List.of(), file, DATE))
        .isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void given_values_when_format_then_fourDecimalsOrNa() {
    assertThat(Comparer.format(0.123456)).isEqualTo("0.1235");
    assertThat(Comparer.format(Double.NaN)).isEqualTo(Comparer.NOT_AVAILABLE);
    assertThat(Comparer.format(null)).isEqualTo(Comparer.NOT_AVAILABLE);
  }
}
