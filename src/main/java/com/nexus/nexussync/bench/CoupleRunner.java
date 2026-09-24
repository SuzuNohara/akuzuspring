package com.nexus.nexussync.bench;

import com.nexus.nexussync.NexussyncException;
import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.ann.RunIds;
import com.nexus.nexussync.ann.RunLock;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.ContextBuilder;
import com.nexus.nexussync.context.HistoryEntry;
import com.nexus.nexussync.context.Profile;
import com.nexus.nexussync.context.ProfileLoader;
import com.nexus.nexussync.context.Weather;
import com.nexus.nexussync.decision.Decision;
import com.nexus.nexussync.decision.DecisionResolver;
import com.nexus.nexussync.decision.TruthRanker;
import com.nexus.nexussync.learning.WeightStore;
import com.nexus.nexussync.learning.WeightUpdater;
import com.nexus.nexussync.learning.Weights;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.places.PlaceAssigner;
import com.nexus.nexussync.places.PlaceAssignment;
import com.nexus.nexussync.rounds.Closure;
import com.nexus.nexussync.rounds.Gate;
import com.nexus.nexussync.rounds.GateExecutor;
import com.nexus.nexussync.rounds.GateResult;
import com.nexus.nexussync.sampler.Sample;
import com.nexus.nexussync.sampler.SampleItem;
import com.nexus.nexussync.sampler.Sampler;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the full pipeline for one couple, {@code roundsSim} simulated dates in a row (unit U11,
 * §3.11).
 *
 * <p>Per iteration: context → effective weights ({@code WeightStore.load} under {@code
 * params.hash()}, or {@code WeightUpdater.initial}) → sample → gate ({@link Gate#run(Sample,
 * Context, Catalog, Params, GateExecutor, Path, CallBudget)}, always with the catalog, D-22) →
 * simulated decision ({@link TruthRanker} for A and B, then {@link DecisionResolver}) → places for
 * the chosen activity → learning ({@code WeightUpdater.update}, saved in {@code outDir/weights/}) →
 * extended history. The simulated rating of the chosen activity is {@value #RATING_BOTH} when it is
 * the truth top-1 of both persons, {@value #RATING_ONE} when of one and {@value #RATING_NONE}
 * otherwise; both profiles receive one history entry per final id (offered, chosen or not, rated
 * only when chosen) dated {@code today}, which stays the day of the couple file in every iteration.
 *
 * <p>Each iteration writes {@code outDir/<experiment>/<coupleId>/<runId>/} with {@value #PARAMS},
 * {@value #CONTEXT}, {@code sample.json} (written by the gate), {@code round*.ann} and {@code
 * round*.out.md} (written by the executor), {@value #GATE}, {@value #DECISION}, {@value #PLACES},
 * {@value #WEIGHTS} and {@value #RECORD} (the {@link RunRecord}, read back by {@link Comparer}). An
 * empty sample skips the gate (no agent call) and closes as {@code AI_UNAVAILABLE}. The {@link
 * RunLock} of {@code params.runtime().nexussyncDir()} is held during the whole call (A7). The
 * random source is a reproducible simulation input, not a security primitive.
 */
public final class CoupleRunner {

  /** Canonical YAML of the parameters. */
  static final String PARAMS = "params.yml";

  /** Context of the couple, profiles included (bench trace, never shown to the agents). */
  static final String CONTEXT = "context.json";

  /** Outcome of the gate. */
  static final String GATE = "gate.json";

  /** Simulated decision, {@code null} when there was none. */
  static final String DECISION = "decision.json";

  /** Places of the chosen activity, {@code null} when there was no decision. */
  static final String PLACES = "places.json";

  /** Weights after the run (before it when nothing was learned). */
  static final String WEIGHTS = "weights.yml";

  /** Serialized {@link RunRecord}. */
  static final String RECORD = "record.json";

  /** Directory of the learned weights under {@code outDir}. */
  static final String WEIGHTS_DIR = "weights";

  /** Rating when the chosen activity is the truth top-1 of both persons. */
  static final int RATING_BOTH = 5;

  /** Rating when the chosen activity is the truth top-1 of one person. */
  static final int RATING_ONE = 4;

  /** Rating when the chosen activity is the truth top-1 of neither person. */
  static final int RATING_NONE = 3;

  private static final Logger LOG = LoggerFactory.getLogger(CoupleRunner.class);
  private static final Pattern EXPERIMENT_DIR = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*");

  private final Clock clock;
  private final Gate gate = new Gate();

  /**
   * Creates a runner timed by the UTC system clock.
   *
   * @implNote O(1) time and space.
   */
  public CoupleRunner() {
    this(Clock.systemUTC());
  }

  /**
   * Creates a runner timed by {@code clock} (run ids and elapsed times).
   *
   * @param clock clock of the runs, never {@code null}
   * @implNote O(1) time and space.
   */
  public CoupleRunner(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Runs {@code roundsSim} iterations for the couple of {@code coupleJson}.
   *
   * @param coupleJson couple file {@code {"a", "b", "today"}}
   * @param p parameters of the experiment
   * @param cat catalog
   * @param ex executor of the agent rounds
   * @param outDir root of the runs (normally {@code nexussync/runs})
   * @param roundsSim number of iterations, {@code >= 0}
   * @param rng seeded random source of the experiment
   * @param budget call budget of this invocation (A2)
   * @return one record per iteration, in order
   * @throws NexussyncException if the couple file is invalid, the lock is held, a file cannot be
   *     written or a pipeline step fails
   * @implNote O(roundsSim · cost of one iteration) time; O(roundsSim) records kept in memory.
   */
  public List<RunRecord> run(
      Path coupleJson,
      Params p,
      Catalog cat,
      GateExecutor ex,
      Path outDir,
      int roundsSim,
      Random rng,
      CallBudget budget)
      throws NexussyncException {
    if (roundsSim < 0) {
      throw new IllegalArgumentException("roundsSim must be >= 0: " + roundsSim);
    }
    ProfileLoader.CoupleFile couple = ProfileLoader.loadCouple(coupleJson);
    Env env = new Env(p, cat, ex, outDir, rng, budget);
    try (RunLock lock = RunLock.acquire(p.runtime().nexussyncDir())) {
      LOG.debug("holding {} for {} iterations", lock.path(), roundsSim);
      List<RunRecord> out = new ArrayList<>();
      Profile a = couple.a();
      Profile b = couple.b();
      long lastMillis = Long.MIN_VALUE;
      for (int i = 0; i < roundsSim; i++) {
        long millis = Math.max(clock.millis(), lastMillis + 1);
        Step step = iterate(a, b, couple.today(), millis, env);
        out.add(step.record());
        a = step.a();
        b = step.b();
        lastMillis = millis;
      }
      return out;
    }
  }

  private Step iterate(Profile a, Profile b, LocalDate today, long millis, Env env)
      throws NexussyncException {
    long start = clock.millis();
    Params p = env.p();
    Context ctx = ContextBuilder.build(a, b, today, p, Map.of());
    Path runDir = open(ctx, millis, env);
    Weights before = weights(ctx, env);
    Map<Feature, Double> w =
        WeightUpdater.effective(before, ctx.ratedDatesCount(), p.learning(), p.sampler());
    Sample sample = Sampler.sample(ctx, env.cat(), w, p.sampler(), env.rng());
    GateResult result = gate(sample, ctx, runDir, env);
    Optional<Decision> decision = decide(result, ctx, env);
    Optional<PlaceAssignment> places = decision.map(d -> place(d.chosen(), ctx, env));
    OptionalInt rating = decision.map(CoupleRunner::rating).orElse(OptionalInt.empty());
    Optional<Weights> after = learn(before, decision, rating, sample, result, env);
    RunRecord record =
        new RunRecord(
            String.valueOf(runDir.getFileName()),
            ctx.coupleId(),
            p.experiment(),
            p.hash(),
            p.seed(),
            sample,
            result,
            decision,
            places,
            before,
            after,
            Duration.ofMillis(Math.max(0, clock.millis() - start)),
            RunRecord.Evidence.of(ctx, result, places, env.cat(), p.rounds().intersection()));
    writeOutcome(runDir, record);
    Outcome o = new Outcome(result, decision, rating, today);
    return new Step(record, extend(a, o), extend(b, o));
  }

  /** Names the run, creates its directory and writes the parameters and the context. */
  private static Path open(Context ctx, long millis, Env env) throws NexussyncException {
    Params p = env.p();
    if (!EXPERIMENT_DIR.matcher(p.experiment()).matches()) {
      throw new NexussyncException("experiment is not a directory name: " + p.experiment());
    }
    String runId = RunIds.of(p.experiment(), ctx.coupleId(), millis, p.seed());
    Path runDir = env.outDir().resolve(p.experiment()).resolve(ctx.coupleId()).resolve(runId);
    BenchIo.write(BenchIo.CANONICAL_YAML, runDir.resolve(PARAMS), p);
    BenchIo.write(BenchIo.JSON, runDir.resolve(CONTEXT), ctx);
    return runDir;
  }

  private Weights weights(Context ctx, Env env) throws NexussyncException {
    Params p = env.p();
    try {
      return WeightStore.load(ctx.coupleId(), p.hash(), env.weightsDir())
          .orElseGet(
              () ->
                  new Weights(ctx.coupleId(), p.hash(), WeightUpdater.initial(p.sampler()), 0, 0));
    } catch (IOException e) {
      throw new NexussyncException("cannot load the weights of " + ctx.coupleId(), e);
    }
  }

  private GateResult gate(Sample sample, Context ctx, Path runDir, Env env)
      throws NexussyncException {
    if (sample.items().isEmpty()) {
      BenchIo.write(BenchIo.JSON, runDir.resolve("sample.json"), Map.of("items", List.of()));
      return new GateResult(
          List.of(), Closure.AI_UNAVAILABLE, List.of(), List.of(), List.of(), Map.of(), Map.of());
    }
    return gate.run(sample, ctx, env.cat(), env.p(), env.ex(), runDir, env.budget());
  }

  private static Optional<Decision> decide(GateResult result, Context ctx, Env env)
      throws NexussyncException {
    List<String> finalIds = result.finalIds();
    if (finalIds.isEmpty()
        || ctx.a().truthWeights().isEmpty()
        || ctx.b().truthWeights().isEmpty()) {
      return Optional.empty();
    }
    Params p = env.p();
    double noise = p.bench().truthNoise();
    List<String> rankA =
        TruthRanker.rank(finalIds, ctx.a(), env.cat(), ctx, p.sampler(), noise, env.rng());
    List<String> rankB =
        TruthRanker.rank(finalIds, ctx.b(), env.cat(), ctx, p.sampler(), noise, env.rng());
    return Optional.of(DecisionResolver.resolve(finalIds, rankA, rankB, p.decision(), env.rng()));
  }

  private static PlaceAssignment place(String chosen, Context ctx, Env env) {
    Weather weather = ctx.weather().getOrDefault(0, Weather.UNKNOWN);
    return PlaceAssigner.assign(
        chosen, ctx, env.cat(), ctx.windows().get(0), weather, env.p().place());
  }

  /**
   * Simulated rating of the chosen activity: {@value #RATING_BOTH}, {@value #RATING_ONE} or {@value
   * #RATING_NONE} as it is the truth top-1 of both, one or neither person.
   */
  static OptionalInt rating(Decision d) {
    boolean topA = !d.rankA().isEmpty() && d.rankA().get(0).equals(d.chosen());
    boolean topB = !d.rankB().isEmpty() && d.rankB().get(0).equals(d.chosen());
    if (topA && topB) {
      return OptionalInt.of(RATING_BOTH);
    }
    return OptionalInt.of(topA || topB ? RATING_ONE : RATING_NONE);
  }

  private static Optional<Weights> learn(
      Weights before,
      Optional<Decision> decision,
      OptionalInt rating,
      Sample sample,
      GateResult result,
      Env env)
      throws NexussyncException {
    if (decision.isEmpty()) {
      return Optional.empty();
    }
    Map<String, Map<Feature, Double>> features = new HashMap<>();
    for (SampleItem item : sample.items()) {
      features.putIfAbsent(item.activityId(), item.features());
    }
    List<Map<Feature, Double>> offered =
        result.finalIds().stream().map(id -> features.getOrDefault(id, Map.of())).toList();
    Map<Feature, Double> chosen = features.getOrDefault(decision.get().chosen(), Map.of());
    Params p = env.p();
    Weights after =
        WeightUpdater.update(before, chosen, offered, rating, p.learning(), p.sampler());
    try {
      WeightStore.save(after, env.weightsDir());
    } catch (IOException e) {
      throw new NexussyncException("cannot save the weights of " + after.coupleId(), e);
    }
    return Optional.of(after);
  }

  private static void writeOutcome(Path runDir, RunRecord r) throws NexussyncException {
    BenchIo.write(BenchIo.JSON, runDir.resolve(GATE), r.gate());
    BenchIo.write(BenchIo.JSON, runDir.resolve(DECISION), r.decision());
    BenchIo.write(BenchIo.JSON, runDir.resolve(PLACES), r.places());
    BenchIo.write(BenchIo.WEIGHTS_YAML, runDir.resolve(WEIGHTS), r.after().orElse(r.before()));
    BenchIo.write(BenchIo.JSON, runDir.resolve(RECORD), r);
  }

  /** Adds one entry per final id, dated {@code today}; unchanged without decision. */
  private static Profile extend(Profile person, Outcome o) {
    if (o.decision().isEmpty()) {
      return person;
    }
    String chosen = o.decision().get().chosen();
    List<HistoryEntry> history = new ArrayList<>(person.history());
    for (String id : o.result().finalIds()) {
      boolean isChosen = id.equals(chosen);
      OptionalInt r = isChosen ? o.rating() : OptionalInt.empty();
      history.add(new HistoryEntry(id, o.today(), true, isChosen, r));
    }
    return new Profile(
        person.userId(),
        person.borough(),
        person.location(),
        person.preferences(),
        person.emotionalRecent(),
        history,
        person.constraints(),
        person.truthWeights());
  }

  /** What an iteration adds to the history of both persons. */
  private record Outcome(
      GateResult result, Optional<Decision> decision, OptionalInt rating, LocalDate today) {}

  /** Inputs shared by every iteration of one call. */
  private record Env(
      Params p, Catalog cat, GateExecutor ex, Path outDir, Random rng, CallBudget budget) {

    Path weightsDir() {
      return outDir.resolve(WEIGHTS_DIR);
    }
  }

  /** Record of one iteration and the profiles with the extended history. */
  private record Step(RunRecord record, Profile a, Profile b) {}
}
