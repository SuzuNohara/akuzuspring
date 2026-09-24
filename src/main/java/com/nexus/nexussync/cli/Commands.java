package com.nexus.nexussync.cli;

import com.nexus.nexussync.NexussyncException;
import com.nexus.nexussync.ann.AgentRenderer;
import com.nexus.nexussync.ann.AgentsGuard;
import com.nexus.nexussync.ann.AnnCheck;
import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.ann.ProcessLauncher;
import com.nexus.nexussync.ann.ProgramRenderer;
import com.nexus.nexussync.bench.Comparer;
import com.nexus.nexussync.bench.CoupleRunner;
import com.nexus.nexussync.bench.RunRecord;
import com.nexus.nexussync.bench.Sweep;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.CatalogLoader;
import com.nexus.nexussync.context.ProfileLoader;
import com.nexus.nexussync.params.ExecutorKind;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.params.ParamsException;
import com.nexus.nexussync.params.ParamsLoader;
import com.nexus.nexussync.params.RuntimeParams;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The commands of the nexussync CLI (unit U11, §3.11, A6): all the logic of {@link Main}, testable
 * with a fake launcher and a fake executor factory.
 *
 * <p>Every command loads {@code --params} on top of the {@code default.yml} of the same directory;
 * {@code --dir} overrides {@code runtime.nexussync_dir}, and the relative {@code arkannie_bin} and
 * {@code replay_dir} are resolved against that root. The catalog paths stay relative in the
 * parameters (they take part in {@code Params.hash()}) and are resolved at load time. Exit codes:
 * {@value #OK} ok, {@value #ERROR} error (logged), {@value #USAGE} usage. Nothing is printed to
 * stdout: the run directories and the report are logged with SLF4J.
 *
 * <p>{@code run} uses one {@link CallBudget} of {@code runtime.max_calls} for the whole invocation
 * (A2), one executor and one {@code Random(seed)} per couple (a reproducible simulation input, not
 * a security primitive), and writes under {@code <dir>/runs/}. {@code replay} repeats one recorded
 * run with the {@code REPLAY} executor; its couple file is the one of {@code fixtures/couples/}
 * whose couple id is the name of the parent of {@code --from}, and its output goes to {@code
 * <dir>/runs/replay/}. {@code sweep} expands a calibration matrix and ranks its experiments (Fase
 * C3).
 */
public final class Commands {

  /** Exit code of a successful command. */
  public static final int OK = 0;

  /** Exit code of a failed command. */
  public static final int ERROR = 1;

  /** Exit code of a usage error. */
  public static final int USAGE = 2;

  /** Value of {@code --couples} that selects every couple of {@code fixtures/couples/}. */
  static final String ALL = "all";

  /** Output root of the runs under the nexussync root. */
  static final String RUNS = "runs";

  /** Output root of the replays under {@link #RUNS}. */
  static final String REPLAYS = "replay";

  /** Directory of the reports under the nexussync root. */
  static final String REPORTS = "reports";

  /** Output root of the sweeps under {@link #RUNS}. */
  static final String SWEEPS = "sweep";

  /** Round templates checked by {@code validate}, under {@code <dir>/ann/}. */
  static final List<String> TEMPLATES = List.of("round1.ann.tmpl", "round2.ann.tmpl");

  private static final Logger LOG = LoggerFactory.getLogger(Commands.class);
  private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*");
  private static final String DEFAULTS = "default.yml";
  private static final String JSON = ".json";
  private static final String TEMPLATE_SUFFIX = ".tmpl";

  private final ProcessLauncher launcher;
  private final GateExecutorFactory factory;
  private final Clock clock;
  private final IntFunction<CallBudget> budgets;
  private final LongFunction<Random> rngs;

  /**
   * Creates the commands over a process boundary and an executor factory, timed by the UTC clock.
   *
   * @param launcher process boundary for the arkannie checks, never {@code null}
   * @param factory executor of each couple, never {@code null}
   * @implNote O(1) time and space.
   */
  public Commands(ProcessLauncher launcher, GateExecutorFactory factory) {
    this(launcher, factory, Clock.systemUTC(), CallBudget::new, Random::new);
  }

  /**
   * Test constructor: clock of run ids and report dates, budget of each invocation and seeded
   * random source of each couple.
   *
   * <p>{@code java.util.Random(seed)} is the reproducible simulation input of the design (§3.0,
   * conscious deviation from §5 of the coding standard: nothing here is a security primitive), so
   * it is injected as {@code Random::new} rather than built inline.
   */
  Commands(
      ProcessLauncher launcher,
      GateExecutorFactory factory,
      Clock clock,
      IntFunction<CallBudget> budgets,
      LongFunction<Random> rngs) {
    this.launcher = Objects.requireNonNull(launcher, "launcher");
    this.factory = Objects.requireNonNull(factory, "factory");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.budgets = Objects.requireNonNull(budgets, "budgets");
    this.rngs = Objects.requireNonNull(rngs, "rngs");
  }

  /**
   * Runs the command parsed by {@link ArgParser#parse(String[])}.
   *
   * @param parsed parsed arguments; empty means a usage error
   * @return the exit code of the command, {@value #USAGE} if {@code parsed} is empty or unknown
   * @implNote O(1) time and space besides the command.
   */
  public int execute(Optional<Object> parsed) {
    Object a = parsed.orElse(null);
    if (a instanceof ValidateArgs v) {
      return validate(v);
    }
    if (a instanceof RunArgs r) {
      return run(r);
    }
    if (a instanceof ReplayArgs r) {
      return replay(r);
    }
    if (a instanceof CompareArgs c) {
      return compare(c);
    }
    if (a instanceof SweepArgs w) {
      return sweep(w);
    }
    LOG.error("{}", ArgParser.USAGE);
    return USAGE;
  }

  /**
   * Validates parameters, agents and Ann programs: loads the parameters, checks {@code .agents/}
   * ({@link AgentsGuard}), renders {@code agent.yaml} ({@link AgentRenderer}), warns if the
   * arkannie version is not the pinned one and runs {@code arkannie --check} on both round
   * templates rendered into a temporary directory.
   *
   * @param a arguments
   * @return {@value #OK} if everything is valid, {@value #ERROR} otherwise
   * @implNote O(size of the parameters, agents and templates) time and space, plus three arkannie
   *     launches.
   */
  public int validate(ValidateArgs a) {
    try {
      Params p = load(a.params(), a.dir());
      Path dir = p.runtime().nexussyncDir();
      AgentsGuard.check(dir);
      AgentRenderer.render(dir, p.agents());
      AnnCheck.versionMismatch(p.runtime(), launcher);
      if (!checkPrograms(p)) {
        return ERROR;
      }
      LOG.info("valid: {} ({}) in {}", p.experiment(), p.hash(), dir);
      return OK;
    } catch (NexussyncException | UncheckedIOException e) {
      LOG.error("validate failed: {}", e.getMessage());
      return ERROR;
    }
  }

  /**
   * Runs the pipeline for the selected couples, {@code rounds} dates each.
   *
   * @param a arguments
   * @return {@value #OK} on success; {@value #USAGE} without {@code --couples}; {@value #ERROR} if
   *     a couple does not exist, the lock is held or a step fails
   * @implNote O(c · rounds · cost of one iteration) time, c = number of couples.
   */
  public int run(RunArgs a) {
    if (a.couples().isEmpty()) {
      LOG.error("run needs --couples; {}", ArgParser.USAGE);
      return USAGE;
    }
    try {
      Params p = load(a.params(), a.dir(), a.seed());
      Path dir = p.runtime().nexussyncDir();
      List<Path> couples = couples(dir, a.couples());
      runCouples(p, couples, a.rounds(), dir.resolve(RUNS));
      return OK;
    } catch (NexussyncException | UncheckedIOException | IllegalStateException e) {
      LOG.error("run failed: {}", e.getMessage());
      return ERROR;
    }
  }

  /**
   * Repeats a recorded run without calling any agent ({@code REPLAY} executor over {@code --from}).
   *
   * @param a arguments
   * @return {@value #OK} on success, {@value #ERROR} if {@code --from} is not a run directory of a
   *     known couple or a step fails
   * @implNote O(k + cost of one iteration) time, k = number of couple files scanned.
   */
  public int replay(ReplayArgs a) {
    try {
      Params loaded = load(a.params(), a.dir());
      Path dir = loaded.runtime().nexussyncDir();
      Path from = a.from().toAbsolutePath().normalize();
      if (!Files.isDirectory(from)) {
        throw new NexussyncException("not a run directory: " + from);
      }
      Path couple = coupleOf(dir, from);
      Params p = withExecutor(loaded, ExecutorKind.REPLAY, Optional.of(from));
      runCouples(p, List.of(couple), 1, dir.resolve(RUNS).resolve(REPLAYS));
      return OK;
    } catch (NexussyncException | UncheckedIOException | IllegalStateException e) {
      LOG.error("replay failed: {}", e.getMessage());
      return ERROR;
    }
  }

  /**
   * Writes {@code <dir>/reports/compare-<date>.md} over the experiments {@code <dir>/runs/<exp>}.
   *
   * @param a arguments
   * @return {@value #OK} on success; {@value #USAGE} without experiments; {@value #ERROR} if a name
   *     is not a directory name or the report cannot be written
   * @implNote O(cost of {@link Comparer#compare(List, Path, LocalDate, Path)}).
   */
  public int compare(CompareArgs a) {
    if (a.exps().isEmpty()) {
      LOG.error("compare needs --exps; {}", ArgParser.USAGE);
      return USAGE;
    }
    Path runs = a.dir().toAbsolutePath().normalize().resolve(RUNS);
    List<Path> dirs = new ArrayList<>();
    for (String exp : a.exps()) {
      if (!NAME.matcher(exp).matches()) {
        LOG.error("compare: not an experiment name: {}", exp);
        return ERROR;
      }
      if (!Files.isDirectory(runs.resolve(exp))) {
        LOG.warn("compare: experiment without runs: {}", exp);
      }
      dirs.add(runs.resolve(exp));
    }
    try {
      Path out = a.dir().toAbsolutePath().normalize().resolve(REPORTS);
      Path report = Comparer.compare(dirs, out, LocalDate.now(clock), couplesDir(a.dir()));
      LOG.info("report {}", report);
      return OK;
    } catch (UncheckedIOException e) {
      LOG.error("compare failed: {}", e.getMessage());
      return ERROR;
    }
  }

  /**
   * Sweeps a calibration matrix with {@link Sweep#run}: every generated experiment runs over the
   * couples once per seed with the requested executor (one budget per experiment and seed, A2)
   * under {@code <dir>/runs/sweep/<stage>-<epochMillis>/}; the report goes to {@code
   * <dir>/calibration/}. {@code ORACLE} never calls an agent.
   *
   * @param a arguments
   * @return {@value #OK} once the report is written (also when every experiment is discarded);
   *     {@value #ERROR} if the matrix, thresholds, gold set or a couple is invalid, or a run fails
   * @implNote O(e · s · c · rounds · cost of one iteration) time, e experiments, s seeds, c
   *     couples; O(e · s · c · rounds) records in memory.
   */
  public int sweep(SweepArgs a) {
    try {
      Path dir = a.dir().toAbsolutePath().normalize();
      List<Path> couples = couples(dir, a.couples());
      String stage = Sweep.stage(a.matrix());
      Path outDir = dir.resolve(RUNS).resolve(SWEEPS).resolve(stage + "-" + clock.millis());
      Sweep.Runner runner =
          (file, seed) -> {
            Params loaded = load(file, Optional.of(dir), OptionalLong.of(seed));
            Optional<Path> replay = loaded.runtime().replayDir();
            return runCouples(
                withExecutor(loaded, a.executor(), replay), couples, a.rounds(), outDir);
          };
      Path report = Sweep.run(a.matrix(), dir, a.seeds(), LocalDate.now(clock), runner);
      LOG.info("sweep report {} (runs in {})", report, outDir);
      return OK;
    } catch (NexussyncException | UncheckedIOException | IllegalStateException e) {
      LOG.error("sweep failed: {}", e.getMessage());
      return ERROR;
    }
  }

  private List<RunRecord> runCouples(Params p, List<Path> couples, int rounds, Path outDir)
      throws NexussyncException {
    Path dir = p.runtime().nexussyncDir();
    Catalog cat = CatalogLoader.load(dir.resolve(p.catalogDir()), dir.resolve(p.placesCsv()));
    CallBudget budget = budgets.apply(p.runtime().maxCalls());
    CoupleRunner runner = new CoupleRunner(clock);
    List<RunRecord> out = new ArrayList<>();
    for (Path couple : couples) {
      List<RunRecord> records =
          runner.run(
              couple, p, cat, factory.create(p, cat), outDir, rounds, rngs.apply(p.seed()), budget);
      for (RunRecord r : records) {
        Path runDir = outDir.resolve(r.experiment()).resolve(r.coupleId()).resolve(r.runId());
        LOG.info("run {} closure {}", runDir, r.gate().closure());
      }
      out.addAll(records);
    }
    LOG.info("agent calls used: {} of {}", budget.used(), p.runtime().maxCalls());
    return out;
  }

  /** Renders both round templates into a temporary directory and checks them with arkannie. */
  private boolean checkPrograms(Params p) throws NexussyncException {
    Path tmp;
    try {
      tmp = Files.createTempDirectory("nexussync-validate");
    } catch (IOException e) {
      throw new NexussyncException("cannot create a temporary directory", e);
    }
    try {
      Map<String, String> values = placeholders(tmp, p);
      Path annDir = p.runtime().nexussyncDir().resolve("ann");
      for (String template : TEMPLATES) {
        String name = template.substring(0, template.length() - TEMPLATE_SUFFIX.length());
        Path program = ProgramRenderer.render(annDir.resolve(template), values, tmp.resolve(name));
        if (!AnnCheck.check(program, p.runtime(), launcher)) {
          LOG.error("arkannie --check rejected {}", template);
          return false;
        }
      }
      return true;
    } finally {
      delete(tmp);
    }
  }

  private static Map<String, String> placeholders(Path tmp, Params p) {
    return Map.of(
        "profile_a", tmp.resolve("profile_a.json").toString(),
        "profile_b", tmp.resolve("profile_b.json").toString(),
        "sample", tmp.resolve("sample.json").toString(),
        "view", tmp.resolve("mediator_view.json").toString(),
        "rubric", tmp.resolve("rubric.md").toString(),
        "shortlist", tmp.resolve("shortlist.json").toString(),
        "k_pick", Integer.toString(p.rounds().pickCount()),
        "k_vote", Integer.toString(p.rounds().voteCount()));
  }

  /** Resolves the couple files of {@code --couples}; every slug must exist. */
  static List<Path> couples(Path dir, List<String> slugs) throws NexussyncException {
    Path couplesDir = couplesDir(dir);
    if (slugs.size() == 1 && ALL.equals(slugs.get(0))) {
      List<Path> all = coupleFiles(couplesDir);
      if (all.isEmpty()) {
        throw new NexussyncException("no couples in " + couplesDir);
      }
      return all;
    }
    List<Path> out = new ArrayList<>();
    for (String slug : slugs) {
      Path file = couplesDir.resolve(slug + JSON);
      if (!NAME.matcher(slug).matches() || !Files.isRegularFile(file)) {
        throw new NexussyncException("unknown couple: " + slug);
      }
      out.add(file);
    }
    return out;
  }

  /** The couple file whose couple id is the name of the parent of {@code runDir}. */
  static Path coupleOf(Path dir, Path runDir) throws NexussyncException {
    Path parent = runDir.getParent();
    String coupleId = parent == null ? "" : String.valueOf(parent.getFileName());
    for (Path file : coupleFiles(couplesDir(dir))) {
      ProfileLoader.CoupleFile c = ProfileLoader.loadCouple(file);
      int x = c.a().userId();
      int y = c.b().userId();
      if ((Math.min(x, y) + "-" + Math.max(x, y)).equals(coupleId)) {
        return file;
      }
    }
    throw new NexussyncException("no couple file for couple id '" + coupleId + "' of " + runDir);
  }

  private static Path couplesDir(Path dir) {
    return dir.resolve("fixtures").resolve("couples");
  }

  private static List<Path> coupleFiles(Path couplesDir) throws NexussyncException {
    if (!Files.isDirectory(couplesDir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(couplesDir)) {
      return files
          .filter(f -> String.valueOf(f.getFileName()).endsWith(JSON))
          .filter(Files::isRegularFile)
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new NexussyncException("cannot list " + couplesDir, e);
    }
  }

  /** Loads {@code params} on its sibling {@code default.yml} and resolves the runtime paths. */
  static Params load(Path params, Optional<Path> dir) throws ParamsException {
    return load(params, dir, OptionalLong.empty());
  }

  private static Params load(Path params, Optional<Path> dir, OptionalLong seed)
      throws ParamsException {
    Path file = params.toAbsolutePath().normalize();
    Path parent = Objects.requireNonNull(file.getParent(), "params has no directory");
    Params p = ParamsLoader.load(file, parent.resolve(DEFAULTS));
    RuntimeParams rt = p.runtime();
    Path home = dir.map(d -> d.toAbsolutePath().normalize()).orElse(rt.nexussyncDir());
    RuntimeParams resolved =
        new RuntimeParams(
            rt.executor(),
            home,
            home.resolve(rt.arkannieBin()),
            rt.replayDir().map(home::resolve),
            rt.maxCalls(),
            rt.arkannieVersion());
    return copy(p, seed.orElse(p.seed()), resolved);
  }

  /** {@code p} with another executor kind and replay directory. */
  private static Params withExecutor(Params p, ExecutorKind kind, Optional<Path> replayDir) {
    RuntimeParams rt = p.runtime();
    return copy(
        p,
        p.seed(),
        new RuntimeParams(
            kind,
            rt.nexussyncDir(),
            rt.arkannieBin(),
            replayDir,
            rt.maxCalls(),
            rt.arkannieVersion()));
  }

  private static Params copy(Params p, long seed, RuntimeParams rt) {
    return new Params(
        p.experiment(),
        seed,
        p.catalogDir(),
        p.placesCsv(),
        p.sampler(),
        p.rounds(),
        p.agents(),
        p.decision(),
        p.place(),
        p.learning(),
        p.context(),
        rt,
        p.rubric(),
        p.bench());
  }

  /** Deletes the temporary directory of {@code validate}; a failure is only logged. */
  private static void delete(Path tmp) {
    try (Stream<Path> walk = Files.walk(tmp)) {
      for (Path f : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(f);
      }
    } catch (IOException e) {
      LOG.warn("cannot delete temporary directory {}", tmp, e);
    }
  }
}
