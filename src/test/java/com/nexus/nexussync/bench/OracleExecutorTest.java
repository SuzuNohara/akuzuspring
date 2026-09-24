package com.nexus.nexussync.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.params.AgentsParams;
import com.nexus.nexussync.params.ContextParams;
import com.nexus.nexussync.params.ExecutorKind;
import com.nexus.nexussync.params.ExplorationMethod;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.Fill;
import com.nexus.nexussync.params.Intersection;
import com.nexus.nexussync.params.LearningMethod;
import com.nexus.nexussync.params.MediatorClimate;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.params.RankAggregation;
import com.nexus.nexussync.params.RoundsParams;
import com.nexus.nexussync.params.RubricParams;
import com.nexus.nexussync.params.RuntimeParams;
import com.nexus.nexussync.params.SamplerParams;
import com.nexus.nexussync.rounds.Agent;
import com.nexus.nexussync.rounds.Closure;
import com.nexus.nexussync.rounds.Gate;
import com.nexus.nexussync.rounds.GateResult;
import com.nexus.nexussync.rounds.PickStatus;
import com.nexus.nexussync.sampler.Sample;
import com.nexus.nexussync.sampler.SampleItem;
import com.nexus.nexussync.sampler.SampleStatus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OracleExecutorTest {

  private static final Catalog NO_CATALOG = new Catalog(Map.of(), Map.of(), Map.of());
  private static final Map<Feature, Double> INTEREST = Map.of(Feature.INTEREST, 1.0);
  private static final Map<Feature, Double> PRICE = Map.of(Feature.PRICE, 1.0);
  private static final SamplerParams SAMPLER =
      new SamplerParams(
          40,
          15,
          15.0,
          30,
          2,
          0.25,
          0.6,
          Map.of(Feature.INTEREST, 1.0),
          LearningMethod.NONE,
          0.3,
          ExplorationMethod.NONE,
          0.0,
          5.0,
          false,
          1.0,
          List.of(),
          4);

  @TempDir Path runDir;
  @TempDir Path home;
  @TempDir Path out;

  /** Ten items: INTEREST grows and PRICE falls with the index. */
  private static Sample sample() {
    List<SampleItem> items = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      Map<Feature, Double> x = Map.of(Feature.INTEREST, i / 10.0, Feature.PRICE, 1.0 - i / 10.0);
      items.add(new SampleItem(String.format("s%02d", i), 0.5, x, false));
    }
    return new Sample(items, List.of(), SampleStatus.OK, Map.of());
  }

  private static Params params(int threshold) {
    Path nx = Path.of("no-such-nexussync");
    return new Params(
        "oracle-test",
        7L,
        Path.of("catalog"),
        Path.of("places.csv"),
        SAMPLER,
        new RoundsParams(
            5,
            3,
            5,
            Intersection.TRIPLE,
            threshold,
            Fill.ALTERNATE_AB,
            RankAggregation.RANK_SUM,
            2,
            true),
        new AgentsParams("haiku", 90, "haiku", 120, MediatorClimate.AGGREGATED),
        null,
        null,
        null,
        new ContextParams(7, 90),
        new RuntimeParams(
            ExecutorKind.ARKANNIE, nx, nx.resolve("arkannie"), Optional.empty(), 200, "0.3.0"),
        new RubricParams(Map.of("interes_conjunto", 1.0), Map.of(), Map.of()),
        null);
  }

  private static Context context(
      Optional<Map<Feature, Double>> a, Optional<Map<Feature, Double>> b) {
    return BenchFixtures.context(a, b);
  }

  @SuppressWarnings("unchecked")
  private static List<String> ids(Optional<Envelope> env, String key) {
    return (List<String>) env.orElseThrow().payload().get(key);
  }

  // U13-06
  @Test
  void given_truthWeights_when_round1_then_personasByOwnTruthAndMediatorByMean() {
    OracleExecutor oracle = new OracleExecutor(NO_CATALOG, SAMPLER);
    Context ctx = context(Optional.of(INTEREST), Optional.of(PRICE));

    Map<Agent, Optional<Envelope>> r =
        oracle.round1(runDir, ctx, sample(), params(5), EnumSet.allOf(Agent.class));

    assertThat(ids(r.get(Agent.A), "picks")).containsExactly("s10", "s09", "s08", "s07", "s06");
    assertThat(ids(r.get(Agent.B), "picks")).containsExactly("s01", "s02", "s03", "s04", "s05");
    assertThat(ids(r.get(Agent.M), "picks")).containsExactly("s01", "s02", "s03", "s04", "s05");
    Envelope a = r.get(Agent.A).orElseThrow();
    assertThat(a.id()).isEqualTo("a");
    assertThat(a.status()).isEqualTo("success");
    assertThat(a.payload().get("reasons")).isEqualTo(Collections.nCopies(5, OracleExecutor.REASON));
    assertThat(r.get(Agent.M).orElseThrow().id()).isEqualTo("m");
    assertThat(oracle.billable()).isFalse();
  }

  // U13-06
  @Test
  void given_oracle_when_gateRuns_then_validClosureWithoutConsumingBudget() throws Exception {
    OracleExecutor oracle = new OracleExecutor(NO_CATALOG, SAMPLER);
    Context ctx = context(Optional.of(INTEREST), Optional.of(PRICE));
    CallBudget budget = new CallBudget(200);

    GateResult r = new Gate().run(sample(), ctx, params(5), oracle, runDir, budget);

    assertThat(r.closure()).isIn(Closure.F1, Closure.F2, Closure.F3);
    assertThat(r.finalIds()).hasSize(5);
    assertThat(r.round1()).extracting(pk -> pk.status()).containsOnly(PickStatus.OK);
    assertThat(r.round2()).hasSize(2);
    assertThat(budget.used()).isZero();
  }

  // U13-06
  @Test
  void given_sameTruthAndZeroBudget_when_gateRuns_then_closesF1() throws Exception {
    OracleExecutor oracle = new OracleExecutor(NO_CATALOG, SAMPLER);
    Context ctx = context(Optional.of(INTEREST), Optional.of(INTEREST));
    CallBudget budget = new CallBudget(0);

    GateResult r = new Gate().run(sample(), ctx, params(5), oracle, runDir, budget);

    assertThat(r.closure()).isEqualTo(Closure.F1);
    assertThat(r.finalIds()).containsExactlyInAnyOrder("s10", "s09", "s08", "s07", "s06");
    assertThat(budget.used()).isZero();
  }

  @Test
  void given_shortlist_when_round2_then_votesByTruthCutToVoteCount() {
    OracleExecutor oracle = new OracleExecutor(NO_CATALOG, SAMPLER);
    Context ctx = context(Optional.of(INTEREST), Optional.of(PRICE));
    oracle.round1(runDir, ctx, sample(), params(5), EnumSet.of(Agent.A));

    Map<Agent, Optional<Envelope>> v =
        oracle.round2(runDir, List.of("s02", "s09", "s05", "s07"), params(5));

    assertThat(ids(v.get(Agent.A), "votes")).containsExactly("s09", "s07", "s05");
    assertThat(ids(v.get(Agent.B), "votes")).containsExactly("s02", "s05", "s07");
    assertThat(oracle.round2(runDir, List.of("s02"), params(5)).get(Agent.A))
        .map(env -> env.payload().get("votes"))
        .contains(List.of("s02"));
  }

  @Test
  void given_noRoundOne_when_round2_then_noEnvelopes() {
    OracleExecutor oracle = new OracleExecutor(NO_CATALOG, SAMPLER);

    Map<Agent, Optional<Envelope>> v = oracle.round2(runDir, List.of("s01"), params(5));

    assertThat(v).containsOnlyKeys(Agent.A, Agent.B);
    assertThat(v.values()).allMatch(Optional::isEmpty);
  }

  @Test
  void given_personaWithoutTruth_when_round1_then_noEnvelopeAndMediatorUsesTheOther() {
    OracleExecutor oracle = new OracleExecutor(NO_CATALOG, SAMPLER);
    Context ctx = context(Optional.empty(), Optional.of(INTEREST));

    Map<Agent, Optional<Envelope>> r =
        oracle.round1(runDir, ctx, sample(), params(5), EnumSet.allOf(Agent.class));

    assertThat(r.get(Agent.A)).isEmpty();
    assertThat(ids(r.get(Agent.M), "picks")).containsExactly("s10", "s09", "s08", "s07", "s06");
    Context none = context(Optional.empty(), Optional.empty());
    assertThat(oracle.round1(runDir, none, sample(), params(5), Set.of(Agent.M)).get(Agent.M))
        .isEmpty();
  }

  @Test
  void given_twoWeights_when_mediatorWeights_then_featureWiseMean() {
    Context ctx = context(Optional.of(INTEREST), Optional.of(PRICE));

    Map<Feature, Double> m = OracleExecutor.weights(Agent.M, ctx).orElseThrow();

    assertThat(m).containsEntry(Feature.INTEREST, 0.5).containsEntry(Feature.PRICE, 0.5);
    assertThat(m).containsEntry(Feature.SEASON, 0.0);
  }

  @Test
  void given_nullArguments_when_constructing_then_rejected() {
    assertThatThrownBy(() -> new OracleExecutor(null, SAMPLER))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new OracleExecutor(NO_CATALOG, null))
        .isInstanceOf(NullPointerException.class);
  }

  // U13-06 (catálogo sintético, sin IA)
  @Test
  void given_syntheticCouple_when_coupleRunnerWithOracle_then_noCallsAndDecision()
      throws Exception {
    Path nx = BenchFixtures.nexussyncDir();
    Params p = BenchFixtures.params(nx, home);
    Catalog cat = BenchFixtures.catalog(nx, p);
    CallBudget budget = new CallBudget(200);

    List<RunRecord> rs =
        new CoupleRunner(BenchFixtures.ticking())
            .run(
                BenchFixtures.couple(nx, "opuestos"),
                p,
                cat,
                new OracleExecutor(cat, p.sampler()),
                out,
                2,
                new Random(p.seed()),
                budget);

    assertThat(budget.used()).isZero();
    assertThat(rs).hasSize(2);
    for (RunRecord r : rs) {
      assertThat(r.gate().closure()).isIn(Closure.F1, Closure.F2, Closure.F3);
      assertThat(r.gate().round1()).extracting(pk -> pk.status()).containsOnly(PickStatus.OK);
      assertThat(r.decision()).isPresent();
      assertThat(r.evidence().calls()).isZero();
    }
    assertThat(Metrics.of(rs, Map.of()).get(Metrics.CHOSEN_TOP3)).isGreaterThan(0.0);
  }
}
