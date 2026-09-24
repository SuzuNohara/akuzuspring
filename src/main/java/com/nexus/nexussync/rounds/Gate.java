package com.nexus.nexussync.rounds;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.ann.RubricRenderer;
import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.context.Constraints;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.EmotionEntry;
import com.nexus.nexussync.context.HistoryEntry;
import com.nexus.nexussync.context.MediatorView;
import com.nexus.nexussync.context.Profile;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.params.RoundsParams;
import com.nexus.nexussync.sampler.Sample;
import com.nexus.nexussync.sampler.SampleItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Two-round gate between the sample and the couple's decision (unit U7).
 *
 * <p>Round one: both personas pick and the mediator recommends; each failed persona is retried
 * once. Both personas OK: F1 = intersection ({@link Closers#f1}); if it reaches {@code thresholdR1}
 * it closes as {@link Closure#F1}; otherwise, when {@code maxRounds >= 2}, both personas vote over
 * the shortlist (round-one ids outside F1, {@code kVote = min(kVote, |shortlist|)}) and F2 closes
 * if it reaches the threshold; otherwise the fill policy closes as {@link Closure#F3}. One persona
 * failed: with {@code allowTwoAi} and the mediator OK, {@link Closure#DEGRADED_F1} = surviving
 * persona ∩ mediator, then filled; otherwise {@link Closure#AI_UNAVAILABLE}. Both personas failed,
 * or the call budget exhausted before round one: {@link Closure#AI_UNAVAILABLE} with the top {@code
 * finalSize} of the sample by score.
 *
 * <p>Every agent call, retries included, consumes one unit of the {@link CallBudget}. The units of
 * a round or a retry are reserved at once ({@link CallBudget#tryConsume(int)}, D-28): three for
 * round one, two for round two, one per failed persona for the retry; if they cannot all be
 * reserved nothing is consumed and the executor is not called. The retry dispatches only the failed
 * personas (D-24) and its answers are merged with those of the first attempt, so {@link
 * CallBudget#used()} equals the number of real dispatches. An executor that is not {@link
 * GateExecutor#billable() billable} (D-26) never touches the budget.
 *
 * <p>Before anything else the gate writes the traces of the run in {@code runDir} ({@link
 * #writeTraces}): {@value #SAMPLE}, {@value #PROFILE_A}, {@value #PROFILE_B}, {@value #VIEW} and
 * {@value #RUBRIC}. The profiles never carry {@code truthWeights}, locations or anything the bench
 * oracle keeps hidden from the agents.
 */
public final class Gate {

  /**
   * Sample offered to the agents: {@code {"items": [...]}} with the catalog description of every
   * item (D-22), or {@code activity_id} and features when the catalog lacks the activity.
   */
  static final String SAMPLE = "sample.json";

  /** Profile of person A as the persona agent sees it. */
  static final String PROFILE_A = "profile_a.json";

  /** Profile of person B as the persona agent sees it. */
  static final String PROFILE_B = "profile_b.json";

  /** Output of {@link MediatorView#of}. */
  static final String VIEW = "mediator_view.json";

  /** Output of {@link RubricRenderer}. */
  static final String RUBRIC = "rubric.md";

  private static final ObjectMapper JSON =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private static final List<Agent> ROUND1 = List.of(Agent.A, Agent.B, Agent.M);
  private static final Catalog NO_CATALOG = new Catalog(Map.of(), Map.of(), Map.of());
  private static final List<Agent> PERSONAS = List.of(Agent.A, Agent.B);

  /**
   * Creates a stateless gate.
   *
   * @implNote O(1) time and space.
   */
  public Gate() {
    // Stateless: all inputs are method arguments.
  }

  /**
   * Runs the gate over a sample without catalog: {@code sample.json} carries ids and features only.
   * Delegates to {@link #run(Sample, Context, Catalog, Params, GateExecutor, Path, CallBudget)}
   * with an empty catalog.
   *
   * @param s sample offered to the agents
   * @param ctx couple context, handed to the executor
   * @param p parameters of the experiment (the gate reads {@link Params#rounds()})
   * @param ex executor of the agent rounds
   * @param runDir directory of the run: receives the traces and is handed to the executor
   * @param budget call budget shared by the whole run
   * @return the final list, its closure and the parsed picks
   * @throws AnnException {@code RENDER} if a trace cannot be written; any error of the executor
   * @implNote O(n log n) time and O(n) space in the sample size, plus at most five agent calls.
   */
  public GateResult run(
      Sample s, Context ctx, Params p, GateExecutor ex, Path runDir, CallBudget budget)
      throws AnnException {
    return run(s, ctx, NO_CATALOG, p, ex, runDir, budget);
  }

  /**
   * Runs the gate over a sample, describing every sampled activity from the catalog in {@code
   * sample.json} (D-22).
   *
   * @param s sample offered to the agents
   * @param ctx couple context, handed to the executor
   * @param cat catalog holding the sampled activities
   * @param p parameters of the experiment (the gate reads {@link Params#rounds()})
   * @param ex executor of the agent rounds
   * @param runDir directory of the run: receives the traces and is handed to the executor
   * @param budget call budget shared by the whole run
   * @return the final list, its closure and the parsed picks
   * @throws AnnException {@code RENDER} if a trace cannot be written; any error of the executor
   * @implNote O(n log n) time and O(n) space in the sample size, plus at most five agent calls.
   */
  public GateResult run(
      Sample s, Context ctx, Catalog cat, Params p, GateExecutor ex, Path runDir, CallBudget budget)
      throws AnnException {
    writeTraces(runDir, ctx, s, cat, p);
    RoundsParams rp = p.rounds();
    List<String> sampleIds = s.items().stream().map(SampleItem::activityId).distinct().toList();
    Set<String> offered = new LinkedHashSet<>(sampleIds);
    if (!reserve(ex, budget, ROUND1.size())) {
      return unavailable(s, rp, sampleIds, List.of());
    }
    Map<Agent, Pick> picks =
        parse(
            ex.round1(runDir, ctx, s, p, EnumSet.copyOf(ROUND1)), ROUND1, offered, rp.pickCount());
    List<Agent> retry = retryable(picks, ex, budget);
    if (!retry.isEmpty()) {
      Map<Agent, Optional<Envelope>> again = ex.round1(runDir, ctx, s, p, EnumSet.copyOf(retry));
      picks.putAll(parse(again, retry, offered, rp.pickCount()));
    }
    List<Pick> round1 = ROUND1.stream().map(picks::get).toList();
    Pick a = picks.get(Agent.A);
    Pick b = picks.get(Agent.B);
    if (!a.ok() && !b.ok()) {
      return unavailable(s, rp, sampleIds, round1);
    }
    Round r = new Round(rp, sampleIds, round1, a, b, picks.get(Agent.M));
    if (!a.ok() || !b.ok()) {
      return degraded(s, r);
    }
    return full(r, ex, runDir, p, budget);
  }

  private GateResult degraded(Sample s, Round r) {
    Pick survivor = r.a().ok() ? r.a() : r.b();
    if (!r.rp().allowTwoAi() || !r.m().ok()) {
      return unavailable(s, r.rp(), r.sampleIds(), r.round1());
    }
    List<String> ordered =
        top(RankAggregator.order(Closers.intersect(survivor, r.m()), r.round1(), r.rp()), r.rp());
    List<String> filled = Closers.f3(ordered, r.a(), r.b(), r.m(), r.rp());
    return finish(r, Closure.DEGRADED_F1, filled, List.of());
  }

  private GateResult full(Round r, GateExecutor ex, Path runDir, Params p, CallBudget budget)
      throws AnnException {
    RoundsParams rp = r.rp();
    List<String> f1 = Closers.f1(r.a(), r.b(), r.m(), rp);
    if (f1.size() >= rp.thresholdR1()) {
      return finish(r, Closure.F1, top(RankAggregator.order(f1, r.round1(), rp), rp), List.of());
    }
    List<String> shortlist = shortlist(r.round1(), f1);
    List<Pick> votes = List.of();
    List<String> current = f1;
    if (rp.maxRounds() >= 2 && !shortlist.isEmpty() && reserve(ex, budget, PERSONAS.size())) {
      int voteK = Math.min(rp.voteCount(), shortlist.size());
      Map<Agent, Pick> v =
          parse(ex.round2(runDir, shortlist, p), PERSONAS, new LinkedHashSet<>(shortlist), voteK);
      votes = List.of(v.get(Agent.A), v.get(Agent.B));
      current = Closers.f2(f1, v.get(Agent.A), v.get(Agent.B));
      if (current.size() >= rp.thresholdR1()) {
        return finish(r, Closure.F2, top(RankAggregator.order(current, r.round1(), rp), rp), votes);
      }
    }
    List<String> ordered = top(RankAggregator.order(current, r.round1(), rp), rp);
    return finish(r, Closure.F3, Closers.f3(ordered, r.a(), r.b(), r.m(), rp), votes);
  }

  private static GateResult finish(
      Round r, Closure closure, List<String> finalIds, List<Pick> round2) {
    Map<String, Map<Agent, String>> reasons = new LinkedHashMap<>();
    for (String id : finalIds) {
      Map<Agent, String> byAgent = new EnumMap<>(Agent.class);
      r.round1().stream()
          .filter(pk -> pk.ok() && pk.ids().contains(id))
          .forEach(pk -> byAgent.put(pk.agent(), pk.reasons().get(pk.ids().indexOf(id))));
      reasons.put(id, byAgent);
    }
    return new GateResult(
        finalIds,
        closure,
        RankAggregator.remaining(r.sampleIds(), finalIds, r.round1()),
        r.round1(),
        round2,
        RankAggregator.rankSum(finalIds, r.round1(), r.rp().pickCount()),
        reasons);
  }

  private static GateResult unavailable(
      Sample s, RoundsParams rp, List<String> sampleIds, List<Pick> round1) {
    List<String> best =
        s.items().stream()
            .sorted(
                Comparator.comparingDouble(SampleItem::score)
                    .reversed()
                    .thenComparing(SampleItem::activityId))
            .map(SampleItem::activityId)
            .distinct()
            .limit(Math.max(0, rp.finalSize()))
            .toList();
    Round r =
        new Round(
            rp,
            sampleIds,
            round1,
            Pick.failed(Agent.A, List.of()),
            Pick.failed(Agent.B, List.of()),
            Pick.failed(Agent.M, List.of()));
    return finish(r, Closure.AI_UNAVAILABLE, best, List.of());
  }

  private static Map<Agent, Pick> parse(
      Map<Agent, Optional<Envelope>> envelopes, List<Agent> agents, Set<String> offered, int k) {
    Map<Agent, Pick> out = new EnumMap<>(Agent.class);
    for (Agent ag : agents) {
      Optional<Envelope> env = envelopes.getOrDefault(ag, Optional.empty());
      out.put(ag, PickParser.parse(ag, env, offered, k));
    }
    return out;
  }

  private static List<String> shortlist(List<Pick> round1, List<String> f1) {
    Set<String> out = new LinkedHashSet<>();
    round1.stream().filter(Pick::ok).forEach(pk -> out.addAll(pk.ids()));
    f1.forEach(out::remove);
    return new ArrayList<>(out);
  }

  private static List<Agent> retryable(Map<Agent, Pick> picks, GateExecutor ex, CallBudget budget) {
    List<Agent> failed = PERSONAS.stream().filter(persona -> !picks.get(persona).ok()).toList();
    if (failed.isEmpty() || !reserve(ex, budget, failed.size())) {
      return List.of();
    }
    return failed;
  }

  /** Reserves {@code calls} units at once (D-28); a non-billable executor needs none (D-26). */
  private static boolean reserve(GateExecutor ex, CallBudget budget, int calls) {
    return !ex.billable() || budget.tryConsume(calls);
  }

  private static List<String> top(List<String> ids, RoundsParams rp) {
    return ids.subList(0, Math.min(ids.size(), Math.max(0, rp.finalSize())));
  }

  /**
   * Writes the five traces of a run in {@code runDir}, overwriting them: the sample, both profiles
   * without {@code truthWeights} nor location, the mediator view and the rubric. The rubric carries
   * the normative text of {@code specs/rubric-mediador.md} when that file exists under {@code
   * p.runtime().nexussyncDir()}, and only the parameter tables otherwise.
   *
   * @param runDir directory of the run, created if missing
   * @param ctx couple context
   * @param s sample offered to the agents
   * @param p parameters of the experiment ({@code agents}, {@code context}, {@code rubric} and
   *     {@code runtime} are read)
   * @throws AnnException {@code RENDER} if a file cannot be written
   * @implNote O(n + h + r) time and space, n sample items, h history entries, r rubric entries.
   */
  static void writeTraces(Path runDir, Context ctx, Sample s, Params p) throws AnnException {
    writeTraces(runDir, ctx, s, NO_CATALOG, p);
  }

  /**
   * Writes the five traces like {@link #writeTraces(Path, Context, Sample, Params)}, with {@code
   * sample.json} describing each item from {@code cat} (D-22).
   *
   * @param runDir directory of the run, created if missing
   * @param ctx couple context
   * @param s sample offered to the agents
   * @param cat catalog holding the sampled activities; an absent activity keeps ids and features
   * @param p parameters of the experiment
   * @throws AnnException {@code RENDER} if a file cannot be written
   * @implNote O(n + h + r) time and space, n sample items, h history entries, r rubric entries.
   */
  static void writeTraces(Path runDir, Context ctx, Sample s, Catalog cat, Params p)
      throws AnnException {
    json(runDir.resolve(SAMPLE), sampleView(s, cat));
    json(runDir.resolve(PROFILE_A), profileView(ctx.a()));
    json(runDir.resolve(PROFILE_B), profileView(ctx.b()));
    json(runDir.resolve(VIEW), MediatorView.of(ctx, p));
    Path nexussyncDir = p.runtime().nexussyncDir();
    if (Files.isRegularFile(nexussyncDir.resolve("specs").resolve("rubric-mediador.md"))) {
      RubricRenderer.render(p.rubric(), nexussyncDir, runDir.resolve(RUBRIC));
    } else {
      RubricRenderer.render(p.rubric(), runDir.resolve(RUBRIC));
    }
  }

  private static Map<String, Object> sampleView(Sample s, Catalog cat) {
    List<Map<String, Object>> items = new ArrayList<>();
    for (SampleItem item : s.items()) {
      Activity a = cat.activities().get(item.activityId());
      if (a == null) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activity_id", item.activityId());
        m.put("features", new TreeMap<>(item.features()));
        items.add(m);
      } else {
        items.add(activityView(a));
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("items", items);
    return out;
  }

  /** Catalog description of an activity, without features nor score (no ranking bias). */
  private static Map<String, Object> activityView(Activity a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("activity_id", a.activityId());
    m.put("title", a.title());
    m.put("activity_type", a.activityType());
    m.put("location_scope", a.locationScope().name());
    m.put("interests", new TreeSet<>(a.interests()));
    m.put("dayparts", a.dayparts().stream().map(Enum::name).sorted().toList());
    m.put("duration_avg", a.durationAvg());
    m.put("cost_mxn_pp", a.costMxnPp());
    m.put("price_band", a.priceBand());
    m.put("outdoor", a.outdoor());
    m.put("ambience", new TreeSet<>(a.ambience()));
    m.put("description", a.description());
    return m;
  }

  private static Map<String, Object> profileView(Profile person) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("user_id", person.userId());
    m.put("borough", person.borough());
    m.put("preferences", new TreeMap<>(person.preferences()));
    m.put("constraints", constraintsView(person.constraints()));
    List<Map<String, Object>> emotions = new ArrayList<>();
    for (EmotionEntry e : person.emotionalRecent()) {
      Map<String, Object> em = new LinkedHashMap<>();
      em.put("date", e.date().toString());
      em.put("code", e.code());
      em.put("intensity", e.intensity());
      emotions.add(em);
    }
    m.put("emotional_recent", emotions);
    List<Map<String, Object>> history = new ArrayList<>();
    for (HistoryEntry h : person.history()) {
      Map<String, Object> hm = new LinkedHashMap<>();
      hm.put("activity_id", h.activityId());
      hm.put("date", h.date().toString());
      hm.put("offered", h.offered());
      hm.put("chosen", h.chosen());
      h.rating().ifPresent(r -> hm.put("rating", r));
      history.add(hm);
    }
    m.put("history", history);
    return m;
  }

  private static Map<String, Object> constraintsView(Constraints c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("budget_band", c.budgetBand());
    m.put("travel_band", c.travelBand());
    m.put("window_day", c.windowDay().name());
    m.put("window_start", c.windowStart().toString());
    m.put("window_end", c.windowEnd().toString());
    return m;
  }

  private static void json(Path out, Object value) throws AnnException {
    try {
      Path parent = out.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      JSON.writeValue(out.toFile(), value);
    } catch (IOException e) {
      throw new AnnException(AnnException.Kind.RENDER, "cannot write trace " + out, e);
    }
  }

  /** State of a gate run after round one. */
  private record Round(
      RoundsParams rp, List<String> sampleIds, List<Pick> round1, Pick a, Pick b, Pick m) {}
}
