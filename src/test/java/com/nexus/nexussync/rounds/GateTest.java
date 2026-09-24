package com.nexus.nexussync.rounds;

import static com.nexus.nexussync.rounds.FakeGateExecutor.envelope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.Daypart;
import com.nexus.nexussync.catalog.LocationScope;
import com.nexus.nexussync.context.Climate;
import com.nexus.nexussync.context.Constraints;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.EmotionEntry;
import com.nexus.nexussync.context.HistoryEntry;
import com.nexus.nexussync.context.Level;
import com.nexus.nexussync.context.Location;
import com.nexus.nexussync.context.Profile;
import com.nexus.nexussync.params.AgentsParams;
import com.nexus.nexussync.params.ContextParams;
import com.nexus.nexussync.params.ExecutorKind;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.Fill;
import com.nexus.nexussync.params.Intersection;
import com.nexus.nexussync.params.MediatorClimate;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.params.RankAggregation;
import com.nexus.nexussync.params.RoundsParams;
import com.nexus.nexussync.params.RubricParams;
import com.nexus.nexussync.params.RuntimeParams;
import com.nexus.nexussync.sampler.Sample;
import com.nexus.nexussync.sampler.SampleItem;
import com.nexus.nexussync.sampler.SampleStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GateTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
  private static final Context CTX = context();
  private static final List<String> F1_A = List.of("s01", "s02", "s03", "s04", "s05");

  private final Gate gate = new Gate();

  @TempDir Path runDir;

  /**
   * Builds a couple whose profiles carry truth weights, a location and a rating, so the traces can
   * be checked for leaks.
   */
  static Context context() {
    return new Context(
        "1-2",
        profile(1, "museos", 5),
        profile(2, "senderismo", 4),
        new Climate(Level.MID, Level.HIGH),
        new Climate(Level.LOW, Level.MID),
        List.of(),
        Map.of(),
        1,
        TODAY);
  }

  private static Profile profile(int userId, String interest, int level) {
    return new Profile(
        userId,
        "Coyoacan",
        Optional.of(new Location(19.35, -99.16)),
        Map.of(interest, level),
        List.of(new EmotionEntry(TODAY.minusDays(1), "HAPPY", 4)),
        List.of(new HistoryEntry("s09", TODAY.minusDays(3), true, true, OptionalInt.of(5))),
        new Constraints(
            "MID", "NEAR", DayOfWeek.SATURDAY, LocalTime.of(10, 0), LocalTime.of(14, 0)),
        Optional.of(Map.of(Feature.INTEREST, 0.7, Feature.PRICE, 0.3)));
  }

  static Sample sample() {
    List<SampleItem> items = new ArrayList<>();
    for (int i = 10; i >= 1; i--) {
      items.add(new SampleItem(String.format("s%02d", i), 1.0 - i * 0.05, Map.of(), false));
    }
    return new Sample(items, List.of(), SampleStatus.OK, Map.of());
  }

  private static RoundsParams rounds(Fill fill, int voteCount, int maxRounds, boolean allowTwoAi) {
    return new RoundsParams(
        5,
        voteCount,
        5,
        Intersection.TRIPLE,
        5,
        fill,
        RankAggregation.RANK_SUM,
        maxRounds,
        allowTwoAi);
  }

  private static RoundsParams rounds() {
    return rounds(Fill.ALTERNATE_AB, 5, 2, true);
  }

  private static Params params(RoundsParams rp) {
    return params(rp, Path.of("no-such-nexussync"));
  }

  /**
   * Parameters with every section the gate and the executor read; the rest stay {@code null}
   * because neither reads them.
   */
  static Params params(RoundsParams rp, Path nexussyncDir) {
    return new Params(
        "gate-test",
        7L,
        Path.of("catalog"),
        Path.of("places.csv"),
        null,
        rp,
        new AgentsParams("haiku", 90, "haiku", 120, MediatorClimate.AGGREGATED),
        null,
        null,
        null,
        new ContextParams(7, 90),
        new RuntimeParams(
            ExecutorKind.ARKANNIE,
            nexussyncDir,
            nexussyncDir.resolve("arkannie").resolve("bin").resolve("arkannie"),
            Optional.empty(),
            200,
            "0.3.0"),
        new RubricParams(
            Map.of("interes_conjunto", 0.6, "novedad", 0.4),
            Map.of("max_por_tipo", 2),
            Map.of("rechazada_reciente", 1.0)),
        null);
  }

  static RoundsParams defaultRounds() {
    return rounds();
  }

  private GateResult run(FakeGateExecutor ex, RoundsParams rp, CallBudget budget)
      throws AnnException {
    return gate.run(sample(), CTX, params(rp), ex, runDir, budget);
  }

  private static FakeGateExecutor f1Setup() {
    return new FakeGateExecutor()
        .respond(Agent.A, 1, envelope("a", "picks", F1_A.toArray(String[]::new)))
        .respond(Agent.B, 1, envelope("b", "picks", "s05", "s04", "s03", "s02", "s01"))
        .respond(Agent.M, 1, envelope("m", "picks", "s03", "s01", "s02", "s05", "s04"));
  }

  private static FakeGateExecutor f2Setup() {
    return new FakeGateExecutor()
        .respond(Agent.A, 1, envelope("a", "picks", F1_A.toArray(String[]::new)))
        .respond(Agent.B, 1, envelope("b", "picks", "s01", "s02", "s06", "s07", "s08"))
        .respond(Agent.M, 1, envelope("m", "picks", "s01", "s02", "s09", "s03", "s06"));
  }

  // U7-05
  @Test
  void given_intersectionReachesThreshold_when_run_then_closesF1OrderedByRankSum()
      throws AnnException {
    FakeGateExecutor ex = f1Setup();
    CallBudget budget = new CallBudget(200);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.F1);
    assertThat(r.finalIds()).containsExactly("s03", "s01", "s02", "s05", "s04");
    assertThat(r.rankSums())
        .containsExactly(
            entry("s03", 7), entry("s01", 8), entry("s02", 9), entry("s05", 10), entry("s04", 11));
    assertThat(r.remaining()).containsExactly("s10", "s09", "s08", "s07", "s06");
    assertThat(r.reasons().get("s03"))
        .containsExactly(entry(Agent.A, "s03-a"), entry(Agent.B, "s03-b"), entry(Agent.M, "s03-m"));
    assertThat(r.round1()).extracting(Pick::agent).containsExactly(Agent.A, Agent.B, Agent.M);
    assertThat(r.round2()).isEmpty();
    assertThat(ex.round1Calls()).isEqualTo(1);
    assertThat(ex.round2Calls()).isZero();
    assertThat(budget.used()).isEqualTo(3);
  }

  // U7-05
  @Test
  void given_sharedVotesReachThreshold_when_run_then_closesF2() throws AnnException {
    FakeGateExecutor ex =
        f2Setup()
            .respond(Agent.A, 2, envelope("a", "votes", "s03", "s04", "s05", "s06", "s07"))
            .respond(Agent.B, 2, envelope("b", "votes", "s03", "s04", "s05", "s06", "s08"));
    CallBudget budget = new CallBudget(200);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.F2);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s06", "s04");
    assertThat(r.remaining()).containsExactly("s09", "s07", "s05", "s08", "s10");
    assertThat(ex.shortlists())
        .containsExactly(List.of("s03", "s04", "s05", "s06", "s07", "s08", "s09"));
    assertThat(r.round2()).extracting(Pick::agent).containsExactly(Agent.A, Agent.B);
    assertThat(budget.used()).isEqualTo(5);
  }

  // U7-05
  @Test
  void given_disjointVotes_when_run_then_closesF3WithFill() throws AnnException {
    FakeGateExecutor ex =
        f2Setup()
            .respond(Agent.A, 2, envelope("a", "votes", "s03"))
            .respond(Agent.B, 2, envelope("b", "votes", "s04"));

    GateResult r = run(ex, rounds(), new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.F3);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s06", "s04");
    assertThat(r.round2().get(0).ids()).containsExactly("s03");
    assertThat(r.round2().get(1).ids()).containsExactly("s04");
  }

  // U7-05
  @Test
  void given_maxRoundsOne_when_run_then_skipsRoundTwoAndFills() throws AnnException {
    FakeGateExecutor ex = f2Setup();
    CallBudget budget = new CallBudget(200);

    GateResult r = run(ex, rounds(Fill.ALTERNATE_AB, 5, 1, true), budget);

    assertThat(r.closure()).isEqualTo(Closure.F3);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s06", "s04");
    assertThat(r.round2()).isEmpty();
    assertThat(ex.round2Calls()).isZero();
    assertThat(budget.used()).isEqualTo(3);
  }

  // U7-05
  @Test
  void given_emptyShortlist_when_run_then_noRoundTwo() throws AnnException {
    FakeGateExecutor ex =
        new FakeGateExecutor()
            .respond(Agent.A, 1, envelope("a", "picks", "s01", "s02", "s03"))
            .respond(Agent.B, 1, envelope("b", "picks", "s01", "s02", "s03"))
            .respond(Agent.M, 1, envelope("m", "picks", "s01", "s02", "s03"));

    GateResult r = run(ex, rounds(), new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.F3);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03");
    assertThat(ex.round2Calls()).isZero();
  }

  // U7-15
  @Test
  void given_shortlistSmallerThanVoteCount_when_run_then_votesLimitedToShortlist()
      throws AnnException {
    FakeGateExecutor ex =
        new FakeGateExecutor()
            .respond(Agent.A, 1, envelope("a", "picks", F1_A.toArray(String[]::new)))
            .respond(Agent.B, 1, envelope("b", "picks", "s01", "s02", "s03", "s04", "s06"))
            .respond(Agent.M, 1, envelope("m", "picks", "s01", "s02", "s03", "s04", "s07"))
            .respond(Agent.A, 2, envelope("a", "votes", "s07", "s06", "s05", "s01"))
            .respond(Agent.B, 2, envelope("b", "votes", "s06", "s05", "s07"));

    GateResult r = run(ex, rounds(), new CallBudget(200));

    assertThat(ex.shortlists()).containsExactly(List.of("s05", "s06", "s07"));
    assertThat(r.round2().get(0).ids()).containsExactly("s07", "s06", "s05");
    assertThat(r.round2().get(0).hallucinated()).containsExactly("s01");
    assertThat(r.closure()).isEqualTo(Closure.F2);
    assertThat(r.finalIds()).hasSize(5);
  }

  // U7-15
  @Test
  void given_voteCountSmallerThanShortlist_when_run_then_votesCutToVoteCount() throws AnnException {
    FakeGateExecutor ex =
        new FakeGateExecutor()
            .respond(Agent.A, 1, envelope("a", "picks", F1_A.toArray(String[]::new)))
            .respond(Agent.B, 1, envelope("b", "picks", "s01", "s02", "s03", "s04", "s06"))
            .respond(Agent.M, 1, envelope("m", "picks", "s01", "s02", "s03", "s04", "s07"))
            .respond(Agent.A, 2, envelope("a", "votes", "s07", "s06", "s05"))
            .respond(Agent.B, 2, envelope("b", "votes", "s06", "s07", "s05"));

    GateResult r = run(ex, rounds(Fill.ALTERNATE_AB, 2, 2, true), new CallBudget(200));

    assertThat(r.round2().get(0).ids()).containsExactly("s07", "s06");
    assertThat(r.round2().get(1).ids()).containsExactly("s06", "s07");
    assertThat(r.closure()).isEqualTo(Closure.F2);
  }

  // U7-09
  @Test
  void given_personaFailsOnce_when_run_then_retriedOnceAndClosesNormally() throws AnnException {
    FakeGateExecutor ex = f1Setup().failTimes(Agent.A, 1);
    CallBudget budget = new CallBudget(200);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.F1);
    assertThat(r.finalIds()).containsExactly("s03", "s01", "s02", "s05", "s04");
    assertThat(ex.round1Calls()).isEqualTo(2);
    assertThat(ex.round1Agents())
        .containsExactly(Set.of(Agent.A, Agent.B, Agent.M), Set.of(Agent.A));
    assertThat(budget.used()).isEqualTo(4);
  }

  // D-24
  @Test
  void given_bothPersonasFailOnce_when_run_then_retryAsksBothAndKeepsFirstMediator()
      throws AnnException {
    FakeGateExecutor ex = f1Setup().failTimes(Agent.A, 1).failTimes(Agent.B, 1);
    CallBudget budget = new CallBudget(200);

    GateResult r = run(ex, rounds(), budget);

    assertThat(ex.round1Agents())
        .containsExactly(Set.of(Agent.A, Agent.B, Agent.M), Set.of(Agent.A, Agent.B));
    assertThat(budget.used()).isEqualTo(5);
    assertThat(r.closure()).isEqualTo(Closure.F1);
    assertThat(r.round1().get(2).status()).isEqualTo(PickStatus.OK);
    assertThat(runDir.resolve("round1-2.ann")).content().doesNotContain("--id=m");
  }

  // D-22
  @Test
  @SuppressWarnings("unchecked")
  void given_catalog_when_run_then_sampleDescribesActivitiesWithoutFeaturesNorScore()
      throws AnnException, IOException {
    Catalog cat = new Catalog(Map.of("s01", activity("s01")), Map.of(), Map.of());

    gate.run(sample(), CTX, cat, params(rounds()), f1Setup(), runDir, new CallBudget(200));

    Map<String, Object> json =
        new ObjectMapper().readValue(runDir.resolve(Gate.SAMPLE).toFile(), Map.class);
    List<Map<String, Object>> items = (List<Map<String, Object>>) json.get("items");
    Map<String, Object> described = items.get(9);
    assertThat(described)
        .containsOnlyKeys(
            "activity_id",
            "title",
            "activity_type",
            "location_scope",
            "interests",
            "dayparts",
            "duration_avg",
            "cost_mxn_pp",
            "price_band",
            "outdoor",
            "ambience",
            "description")
        .containsEntry("title", "Titulo s01")
        .containsEntry("interests", List.of("arte", "museos"))
        .containsEntry("dayparts", List.of("AFTERNOON", "MORNING"))
        .containsEntry("cost_mxn_pp", 150);
    assertThat(items.get(0)).containsOnlyKeys("activity_id", "features");
    assertThat(Files.readString(runDir.resolve(Gate.SAMPLE), StandardCharsets.UTF_8))
        .doesNotContain("score");
  }

  private static Activity activity(String id) {
    return new Activity(
        id,
        "Titulo " + id,
        "MUSEUM",
        LocationScope.CITY,
        Set.of("MUSEUM"),
        Set.of("museos", "arte"),
        Set.of(Daypart.MORNING, Daypart.AFTERNOON),
        Set.of("ANY"),
        60,
        90,
        120,
        1,
        2,
        5,
        1,
        150,
        "LOW",
        false,
        Set.of("ANY"),
        Set.of("QUIET", "CULTURAL"),
        "Una visita");
  }

  // U7-10
  @Test
  void given_personaFailsTwiceAndTwoAiAllowed_when_run_then_degradedF1Filled() throws AnnException {
    FakeGateExecutor ex =
        new FakeGateExecutor()
            .failTimes(Agent.A, 2)
            .respond(Agent.B, 1, envelope("b", "picks", F1_A.toArray(String[]::new)))
            .respond(Agent.M, 1, envelope("m", "picks", "s01", "s02", "s09", "s08", "s07"));

    GateResult r = run(ex, rounds(), new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.DEGRADED_F1);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s04", "s05");
    assertThat(r.round1().get(0).status()).isEqualTo(PickStatus.FAILED);
    assertThat(ex.round1Calls()).isEqualTo(2);
    assertThat(ex.round2Calls()).isZero();
  }

  // U7-10
  @Test
  void given_personaFailsAndTwoAiNotAllowed_when_run_then_aiUnavailable() throws AnnException {
    FakeGateExecutor ex = f1Setup().failTimes(Agent.B, 2);

    GateResult r = run(ex, rounds(Fill.ALTERNATE_AB, 5, 2, false), new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.AI_UNAVAILABLE);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s04", "s05");
  }

  // U7-10
  @Test
  void given_personaAndMediatorFail_when_run_then_aiUnavailable() throws AnnException {
    FakeGateExecutor ex = f1Setup().failTimes(Agent.B, 2).failTimes(Agent.M, 1);

    GateResult r = run(ex, rounds(), new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.AI_UNAVAILABLE);
    assertThat(r.round1()).hasSize(3);
  }

  // U7-11
  @Test
  void given_mediatorDown_when_run_then_pairIntersectionAndFillWithoutMediator()
      throws AnnException {
    FakeGateExecutor ex =
        new FakeGateExecutor()
            .respond(Agent.A, 1, envelope("a", "picks", F1_A.toArray(String[]::new)))
            .respond(Agent.B, 1, envelope("b", "picks", "s02", "s01", "s06", "s07", "s08"));

    GateResult r = run(ex, rounds(Fill.MEDIATOR_FIRST, 5, 2, true), new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.F3);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s06", "s04");
    assertThat(r.round1().get(2).status()).isEqualTo(PickStatus.FAILED);
    assertThat(r.round2()).extracting(Pick::status).containsOnly(PickStatus.FAILED);
    assertThat(ex.round1Calls()).isEqualTo(1);
  }

  // U7-11
  @Test
  void given_mediatorDownAndPairReachesThreshold_when_run_then_closesF1OnPair()
      throws AnnException {
    FakeGateExecutor ex =
        new FakeGateExecutor()
            .respond(Agent.A, 1, envelope("a", "picks", F1_A.toArray(String[]::new)))
            .respond(Agent.B, 1, envelope("b", "picks", "s02", "s01", "s06", "s07", "s08"));
    RoundsParams rp =
        new RoundsParams(
            5, 5, 5, Intersection.TRIPLE, 2, Fill.ALTERNATE_AB, RankAggregation.RANK_SUM, 2, true);

    GateResult r = run(ex, rp, new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.F1);
    assertThat(r.finalIds()).containsExactly("s01", "s02");
  }

  // U7-12
  @Test
  void given_bothPersonasFail_when_run_then_aiUnavailableWithTopOfSample() throws AnnException {
    FakeGateExecutor ex = f1Setup().failTimes(Agent.A, 2).failTimes(Agent.B, 2);
    CallBudget budget = new CallBudget(200);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.AI_UNAVAILABLE);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s04", "s05");
    assertThat(r.remaining()).containsExactly("s10", "s09", "s08", "s07", "s06");
    assertThat(r.rankSums()).containsEntry("s01", 2);
    assertThat(r.reasons().get("s01")).containsOnlyKeys(Agent.M);
    assertThat(ex.round1Calls()).isEqualTo(2);
    assertThat(budget.used()).isEqualTo(5);
  }

  // U7-12
  @Test
  void given_exhaustedBudget_when_run_then_aiUnavailableWithoutCallingExecutor()
      throws AnnException {
    FakeGateExecutor ex = f1Setup();

    GateResult r = run(ex, rounds(), new CallBudget(0));

    assertThat(r.closure()).isEqualTo(Closure.AI_UNAVAILABLE);
    assertThat(r.round1()).isEmpty();
    assertThat(ex.round1Calls()).isZero();
  }

  // U7-09
  @Test
  void given_noBudgetForRetry_when_run_then_noRetryAndDegrades() throws AnnException {
    FakeGateExecutor ex = f1Setup().failTimes(Agent.A, 1);

    GateResult r = run(ex, rounds(), new CallBudget(3));

    assertThat(ex.round1Calls()).isEqualTo(1);
    assertThat(r.closure()).isEqualTo(Closure.DEGRADED_F1);
  }

  // U7-05
  @Test
  void given_noBudgetForRoundTwo_when_run_then_fillsWithoutVoting() throws AnnException {
    FakeGateExecutor ex = f2Setup();

    GateResult r = run(ex, rounds(), new CallBudget(3));

    assertThat(ex.round2Calls()).isZero();
    assertThat(r.closure()).isEqualTo(Closure.F3);
  }

  // U7-13
  @Test
  void given_budgetExhaustedBeforeRoundOne_when_run_then_aiUnavailableWithoutCalls()
      throws AnnException {
    FakeGateExecutor ex = f1Setup();
    CallBudget budget = new CallBudget(2);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.AI_UNAVAILABLE);
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s04", "s05");
    assertThat(ex.round1Calls()).isZero();
    assertThat(ex.round2Calls()).isZero();
  }

  // U7-13
  @Test
  void given_budgetExhaustedBeforeRoundTwo_when_run_then_f3FromRoundOneWithoutVotes()
      throws AnnException {
    FakeGateExecutor ex = f2Setup();
    CallBudget budget = new CallBudget(4);

    GateResult r = run(ex, rounds(), budget);

    assertThat(r.closure()).isEqualTo(Closure.F3);
    assertThat(r.round2()).isEmpty();
    assertThat(r.finalIds()).containsExactly("s01", "s02", "s03", "s06", "s04");
    assertThat(ex.round2Calls()).isZero();
  }

  // U7-14
  @Test
  void given_run_when_finished_then_runDirHoldsTheFiveTracesWithoutPrivateData()
      throws AnnException, IOException {
    GateResult r = run(f1Setup(), rounds(), new CallBudget(200));

    assertThat(r.closure()).isEqualTo(Closure.F1);
    assertTraces(runDir);
    assertThat(Files.readString(runDir.resolve(Gate.RUBRIC), StandardCharsets.UTF_8))
        .contains("interes_conjunto")
        .doesNotContain("Texto normativo");
  }

  // U7-14
  @Test
  void given_budgetExhausted_when_run_then_tracesStillWritten() throws AnnException, IOException {
    run(f1Setup(), rounds(), new CallBudget(0));

    assertTraces(runDir);
  }

  // U7-14
  @Test
  void given_rubricSpecInNexussyncDir_when_writeTraces_then_rubricCarriesNormativeText(
      @TempDir Path nexussyncDir) throws AnnException, IOException {
    Path specs = Files.createDirectories(nexussyncDir.resolve("specs"));
    Files.writeString(
        specs.resolve("rubric-mediador.md"), "texto de la spec", StandardCharsets.UTF_8);

    Gate.writeTraces(runDir, CTX, sample(), params(rounds(), nexussyncDir));

    assertThat(Files.readString(runDir.resolve(Gate.RUBRIC), StandardCharsets.UTF_8))
        .contains("interes_conjunto")
        .contains("texto de la spec");
  }

  // U7-14
  @Test
  void given_runDirIsRegularFile_when_run_then_renderError(@TempDir Path dir) throws IOException {
    Path file = Files.writeString(dir.resolve("not-a-dir"), "x", StandardCharsets.UTF_8);

    assertThatThrownBy(
            () -> gate.run(sample(), CTX, params(rounds()), f1Setup(), file, new CallBudget(200)))
        .isInstanceOf(AnnException.class);
  }

  /**
   * Checks the five traces of U7-14: all present, profiles without truth weights nor location,
   * mediator view compliant with U3-10 (no emotional entries, ratings or truth weights).
   */
  @SuppressWarnings("unchecked")
  static void assertTraces(Path dir) throws IOException {
    for (String name :
        List.of(Gate.SAMPLE, Gate.PROFILE_A, Gate.PROFILE_B, Gate.VIEW, Gate.RUBRIC)) {
      assertThat(dir.resolve(name)).isRegularFile();
    }
    ObjectMapper json = new ObjectMapper();
    for (String name : List.of(Gate.PROFILE_A, Gate.PROFILE_B)) {
      String text = Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
      assertThat(text)
          .doesNotContain("truth_weights")
          .doesNotContain("truthWeights")
          .doesNotContain("INTEREST")
          .doesNotContain("location")
          .doesNotContain("19.35");
      Map<String, Object> profile = json.readValue(text, Map.class);
      assertThat(profile)
          .containsKeys("user_id", "preferences", "constraints", "history", "emotional_recent");
    }
    String view = Files.readString(dir.resolve(Gate.VIEW), StandardCharsets.UTF_8);
    assertThat(view)
        .contains("preferences_a", "climate_a", "history_agg")
        .doesNotContain("truth")
        .doesNotContain("HAPPY")
        .doesNotContain("\"rating\"")
        .doesNotContain("19.35");
    Map<String, Object> sample = json.readValue(dir.resolve(Gate.SAMPLE).toFile(), Map.class);
    assertThat((List<Map<String, Object>>) sample.get("items"))
        .hasSize(10)
        .allSatisfy(item -> assertThat(item).containsOnlyKeys("activity_id", "features"));
  }
}
