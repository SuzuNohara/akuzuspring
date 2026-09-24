package com.nexus.nexussync.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.nexussync.NexussyncException;
import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.ann.RunLock;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.decision.Decision;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.rounds.Agent;
import com.nexus.nexussync.rounds.Closure;
import com.nexus.nexussync.rounds.FakeGateExecutor;
import com.nexus.nexussync.rounds.GateExecutor;
import com.nexus.nexussync.sampler.Sample;
import com.nexus.nexussync.sampler.SampleStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoupleRunnerTest {

  private static final List<String> TEN_FILES =
      List.of(
          "params.yml",
          "context.json",
          "sample.json",
          "round1.ann",
          "round1.out.md",
          "gate.json",
          "decision.json",
          "places.json",
          "weights.yml",
          "record.json");

  @TempDir Path home;
  @TempDir Path out;

  private Path nx;
  private Params params;
  private Catalog catalog;

  @BeforeEach
  void setUp() throws Exception {
    nx = BenchFixtures.nexussyncDir();
    params = BenchFixtures.params(nx, home);
    catalog = BenchFixtures.catalog(nx, params);
  }

  private List<RunRecord> run(Path couple, Path outDir, int rounds, FakeGateExecutor ex)
      throws NexussyncException {
    return new CoupleRunner(BenchFixtures.ticking())
        .run(
            couple,
            params,
            catalog,
            ex,
            outDir,
            rounds,
            new Random(params.seed()),
            new CallBudget(200));
  }

  private Path runDir(Path outDir, RunRecord r) {
    return outDir.resolve(r.experiment()).resolve(r.coupleId()).resolve(r.runId());
  }

  // U11-01
  @Test
  void given_opuestosAndTwoIterations_when_run_then_twoRecordsTenFilesAndHistoryCarried()
      throws Exception {
    List<RunRecord> rs = run(BenchFixtures.couple(nx, "opuestos"), out, 2, echo());

    assertThat(rs).hasSize(2);
    for (RunRecord r : rs) {
      Path dir = runDir(out, r);
      TEN_FILES.forEach(name -> assertThat(dir.resolve(name)).isRegularFile());
      assertThat(r.gate().closure()).isEqualTo(Closure.F1);
      assertThat(r.decision()).isPresent();
      assertThat(r.places()).isPresent();
    }
    assertThat(rs.get(0).runId()).isNotEqualTo(rs.get(1).runId());
    String chosen = rs.get(0).decision().map(Decision::chosen).orElseThrow();
    JsonNode ctx =
        new ObjectMapper().readTree(runDir(out, rs.get(1)).resolve("context.json").toFile());
    assertThat(ctx.path("a").path("history").findValuesAsText("activityId")).contains(chosen);
    assertThat(ctx.path("b").path("history").findValuesAsText("activityId")).contains(chosen);
    assertThat(rs.get(0).after().orElseThrow().version()).isEqualTo(1);
    assertThat(rs.get(1).before().version()).isEqualTo(1);
    assertThat(rs.get(1).after().orElseThrow().version()).isEqualTo(2);
    assertThat(out.resolve("weights").resolve("101-102.v2.yml")).isRegularFile();
    assertThat(home.resolve(RunLock.LOCK_FILE)).doesNotExist();
    assertThat(rs.get(0).evidence().truthWeights()).hasSize(2);
    assertThat(rs.get(0).evidence().intersectionF1()).isEqualTo(params.rounds().pickCount());
  }

  // U11-02 (completo: todos los ficheros de la corrida; con el reloj de prueba incluso
  // record.json, cuyo elapsed y runId dependen del reloj, es determinista)
  @Test
  void given_sameParamsAndSeed_when_runTwice_then_everyRunFileIdentical(@TempDir Path out2)
      throws Exception {
    RunRecord first = run(BenchFixtures.couple(nx, "opuestos"), out, 1, echo()).get(0);
    RunRecord second = run(BenchFixtures.couple(nx, "opuestos"), out2, 1, echo()).get(0);

    List<String> names = fileNames(runDir(out, first));
    assertThat(names).containsAll(TEN_FILES).isEqualTo(fileNames(runDir(out2, second)));
    for (String name : names) {
      assertThat(Files.readAllBytes(runDir(out2, second).resolve(name)))
          .as(name)
          .isEqualTo(Files.readAllBytes(runDir(out, first).resolve(name)));
    }
    assertThat(Files.readString(runDir(out, first).resolve("params.yml")))
        .contains("experiment: \"baseline-haiku\"")
        .contains("n_sample: 40");
  }

  // D-27
  @Test
  void given_threeIterations_when_run_then_eachIterationOneWeekLaterAndHistoryDatedByIteration()
      throws Exception {
    List<RunRecord> rs = run(BenchFixtures.couple(nx, "opuestos"), out, 3, echo());

    ObjectMapper json = new ObjectMapper();
    List<JsonNode> contexts = new ArrayList<>();
    for (RunRecord r : rs) {
      contexts.add(json.readTree(runDir(out, r).resolve("context.json").toFile()));
    }
    LocalDate today = LocalDate.parse(contexts.get(0).path("today").asText());
    assertThat(contexts)
        .extracting(c -> LocalDate.parse(c.path("today").asText()))
        .containsExactly(today, today.plusDays(7), today.plusDays(14));
    int before = contexts.get(0).path("a").path("history").size();
    JsonNode history = contexts.get(2).path("a").path("history");
    List<LocalDate> added = new ArrayList<>();
    for (int i = before; i < history.size(); i++) {
      added.add(LocalDate.parse(history.get(i).path("date").asText()));
    }
    assertThat(added).isNotEmpty().isSortedAccordingTo(LocalDate::compareTo);
    assertThat(added.stream().distinct()).containsExactly(today, today.plusDays(7));
    assertThat(rs).allSatisfy(r -> assertThat(r.decision()).isPresent());
  }

  // D-30
  @Test
  void given_gateRunning_when_agentsCalled_then_noTruthNorContextInRunDir() throws Exception {
    List<String> seen = new ArrayList<>();
    GateExecutor spy = new TruthSpy(echo(), seen);

    RunRecord r =
        new CoupleRunner(BenchFixtures.ticking())
            .run(
                BenchFixtures.couple(nx, "opuestos"),
                params,
                catalog,
                spy,
                out,
                1,
                new Random(params.seed()),
                new CallBudget(200))
            .get(0);

    assertThat(seen).isNotEmpty().allMatch("clean"::equals);
    assertThat(Files.readString(runDir(out, r).resolve("context.json")))
        .contains(TruthRedaction.CONTEXT_KEY);
    assertThat(r.evidence().calls()).isEqualTo(3);
  }

  // D-33: no file of the runs tree holds the truth weights; context and record carry their hash
  @Test
  void given_finishedRuns_when_walkRunsTree_then_noTruthWeightsAnywhere() throws Exception {
    final List<RunRecord> rs = run(BenchFixtures.couple(nx, "opuestos"), out, 2, echo());

    List<Path> files;
    try (Stream<Path> walk = Files.walk(out)) {
      files = walk.filter(Files::isRegularFile).toList();
    }

    assertThat(files).hasSizeGreaterThanOrEqualTo(2 * TEN_FILES.size());
    for (Path f : files) {
      assertThat(Files.readString(f))
          .as(f.toString())
          .doesNotContain("truth_weights")
          .doesNotContain("truthWeights");
    }
    final Map<Feature, Double> truthA = rs.get(0).evidence().truthWeights().get(Agent.A);
    JsonNode ctx =
        new ObjectMapper().readTree(runDir(out, rs.get(0)).resolve("context.json").toFile());
    assertThat(ctx.path("a").path(TruthRedaction.CONTEXT_KEY).asText())
        .isEqualTo(TruthRedaction.hash(truthA))
        .hasSize(TruthRedaction.HASH_CHARS);
    JsonNode record =
        new ObjectMapper().readTree(runDir(out, rs.get(0)).resolve("record.json").toFile());
    assertThat(record.path("evidence").path(TruthRedaction.RECORD_KEY).path("A").asText())
        .isEqualTo(TruthRedaction.hash(truthA));
    assertThat(rs.get(0).evidence().truthWeights()).hasSize(2);
  }

  /** Executor that inspects {@code runDir} every time an agent round is dispatched (D-30). */
  private record TruthSpy(FakeGateExecutor delegate, List<String> seen) implements GateExecutor {

    @Override
    public Map<Agent, Optional<Envelope>> round1(
        Path runDir, Context ctx, Sample s, Params p, Set<Agent> agents) throws AnnException {
      seen.add(inspect(runDir));
      return delegate.round1(runDir, ctx, s, p, agents);
    }

    @Override
    public Map<Agent, Optional<Envelope>> round2(Path runDir, List<String> shortlist, Params p)
        throws AnnException {
      seen.add(inspect(runDir));
      return delegate.round2(runDir, shortlist, p);
    }

    private static String inspect(Path runDir) {
      if (Files.exists(runDir.resolve("context.json"))) {
        return "context.json present";
      }
      try (Stream<Path> files = Files.walk(runDir)) {
        for (Path f : files.filter(Files::isRegularFile).toList()) {
          String text = Files.readString(f);
          if (text.contains("truth_weights") || text.contains("truthWeights")) {
            return "truth in " + f.getFileName();
          }
        }
        return "clean";
      } catch (IOException e) {
        return "unreadable: " + e.getMessage();
      }
    }
  }

  // U11-01
  @Test
  void given_zeroIterations_when_run_then_emptyAndLockReleased() throws Exception {
    assertThat(run(BenchFixtures.couple(nx, "opuestos"), out, 0, echo())).isEmpty();
    assertThat(home.resolve(RunLock.LOCK_FILE)).doesNotExist();
  }

  @Test
  void given_negativeIterations_when_run_then_illegalArgument() {
    assertThatThrownBy(() -> run(BenchFixtures.couple(nx, "opuestos"), out, -1, echo()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // U11-01 (A7)
  @Test
  void given_lockHeld_when_run_then_lockedAndNothingWritten() throws Exception {
    Files.createFile(home.resolve(RunLock.LOCK_FILE));

    assertThatThrownBy(() -> run(BenchFixtures.couple(nx, "opuestos"), out, 1, echo()))
        .isInstanceOfSatisfying(
            AnnException.class, e -> assertThat(e.kind()).isEqualTo(AnnException.Kind.LOCKED));
    assertThat(out.resolve(params.experiment())).doesNotExist();
  }

  @Test
  void given_coupleWithoutTruthWeights_when_run_then_noDecisionNoLearning(@TempDir Path tmp)
      throws Exception {
    Path couple = edit(tmp, node -> ((ObjectNode) node.get("a")).remove("truth_weights"));

    RunRecord r = run(couple, out, 1, echo()).get(0);

    assertThat(r.decision()).isEmpty();
    assertThat(r.places()).isEmpty();
    assertThat(r.after()).isEmpty();
    assertThat(r.evidence().truthWeights()).hasSize(1);
    assertThat(Files.readString(runDir(out, r).resolve("decision.json")).strip()).isEqualTo("null");
    assertThat(runDir(out, r).resolve("weights.yml")).isRegularFile();
  }

  @Test
  void given_noSharedWindow_when_run_then_aiUnavailableWithoutAgentCalls(@TempDir Path tmp)
      throws Exception {
    Path couple =
        edit(
            tmp,
            node -> ((ObjectNode) node.get("b").get("constraints")).put("window_day", "MONDAY"));
    FakeGateExecutor ex = echo();

    RunRecord r = run(couple, out, 1, ex).get(0);

    assertThat(r.sample().status()).isEqualTo(SampleStatus.NO_WINDOW);
    assertThat(r.gate().closure()).isEqualTo(Closure.AI_UNAVAILABLE);
    assertThat(r.gate().finalIds()).isEmpty();
    assertThat(r.decision()).isEmpty();
    assertThat(ex.round1Calls()).isZero();
    assertThat(runDir(out, r).resolve("sample.json")).isRegularFile();
  }

  @Test
  void given_experimentNotDirectoryName_when_run_then_error() {
    params = BenchFixtures.withExperiment(params, "../escape");

    assertThatThrownBy(() -> run(BenchFixtures.couple(nx, "opuestos"), out, 1, echo()))
        .isInstanceOf(NexussyncException.class)
        .hasMessageContaining("../escape");
    assertThat(home.resolve(RunLock.LOCK_FILE)).doesNotExist();
  }

  @Test
  void given_outDirIsRegularFile_when_run_then_cannotWrite(@TempDir Path tmp) throws Exception {
    Path file = Files.writeString(tmp.resolve("plain"), "x");

    assertThatThrownBy(() -> run(BenchFixtures.couple(nx, "opuestos"), file, 1, echo()))
        .isInstanceOf(NexussyncException.class)
        .hasMessageContaining("cannot write");
    assertThat(home.resolve(RunLock.LOCK_FILE)).doesNotExist();
  }

  @Test
  void given_topOfBothOneOrNone_when_rating_then_fiveFourThree() {
    assertThat(CoupleRunner.rating(decision("x", List.of("x", "y"), List.of("x", "y"))))
        .isEqualTo(OptionalInt.of(5));
    assertThat(CoupleRunner.rating(decision("x", List.of("x", "y"), List.of("y", "x"))))
        .isEqualTo(OptionalInt.of(4));
    assertThat(CoupleRunner.rating(decision("x", List.of("y", "x"), List.of("x", "y"))))
        .isEqualTo(OptionalInt.of(4));
    assertThat(CoupleRunner.rating(decision("x", List.of("y", "x"), List.of("y", "x"))))
        .isEqualTo(OptionalInt.of(3));
    assertThat(CoupleRunner.rating(decision("x", List.of(), List.of())))
        .isEqualTo(OptionalInt.of(3));
  }

  @Test
  void given_defaultConstructor_when_created_then_usable() {
    assertThat(new CoupleRunner()).isNotNull();
  }

  private static Decision decision(String chosen, List<String> rankA, List<String> rankB) {
    return new Decision(chosen, Map.of(chosen, 1), rankA, rankB, false, List.of());
  }

  private static List<String> fileNames(Path dir) throws IOException {
    try (Stream<Path> files = Files.walk(dir)) {
      return files
          .filter(Files::isRegularFile)
          .map(f -> dir.relativize(f).toString())
          .sorted()
          .toList();
    }
  }

  private static FakeGateExecutor echo() {
    return new FakeGateExecutor().echoSample();
  }

  private Path edit(Path tmp, java.util.function.Consumer<JsonNode> change) throws Exception {
    ObjectMapper json = new ObjectMapper();
    JsonNode node = json.readTree(BenchFixtures.couple(nx, "opuestos").toFile());
    change.accept(node);
    Path file = tmp.resolve("couple.json");
    json.writeValue(file.toFile(), node);
    return file;
  }
}
