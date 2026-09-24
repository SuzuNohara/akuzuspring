package com.nexus.nexussync.bench;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.nexus.nexussync.NexussyncException;
import com.nexus.nexussync.params.ParamsException;
import com.nexus.nexussync.params.ParamsLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sweep generator of the calibration (unit U13, Fase C3, U13-05): expands a matrix {@code
 * calibration/matrix-<stage>.yml} into one experiment file per value.
 *
 * <p>Matrix format (every key required, no other key allowed):
 *
 * <pre>
 * stage: 1-sampler              # [a-z0-9][a-z0-9-]*, part of every generated name
 * base: default.yml             # file of params/ the experiments start from
 * mode: one_at_a_time           # the only mode: each value changes ONE parameter of the base
 * params:
 *   sampler.eps0: [0.15, 0.35]  # YAML path in snake_case → scalar values
 *   sampler.weights_init:       # or labelled values: {label, value} sets the path to value
 *     - {label: plano, value: {INTEREST: 1.0, …}}
 *   learning.eta_pos_neg:       # or {label, set: {path: value, …}}: several paths at once
 *     - {label: 0.45-0.15, set: {learning.eta_pos: 0.45, learning.eta_neg: 0.15}}
 * </pre>
 *
 * <p>Each value produces {@code params/cal-<stage>-<param>-<value>.yml}, {@code <param>} being the
 * key with {@code .} and {@code _} turned into {@code -}, and {@code <value>} the label (or the
 * scalar in lower case, {@code _} turned into {@code -}). The file holds the base experiment
 * (nothing when the base is {@code default.yml}) plus that single override, with {@code experiment}
 * set to the file stem. Every file is validated with {@link ParamsLoader} on top of {@code
 * params/default.yml} before any is written; an invalid value, an unknown path, a malformed matrix
 * or two values with the same name raise {@link ParamsException} and nothing is written.
 */
public final class Sweep {

  /** The only supported mode. */
  static final String ONE_AT_A_TIME = "one_at_a_time";

  /** Defaults every experiment is loaded on. */
  static final String DEFAULTS = "default.yml";

  /** Prefix of the generated experiment names. */
  static final String PREFIX = "cal-";

  private static final String STAGE = "stage";
  private static final String BASE = "base";
  private static final String MODE = "mode";
  private static final String PARAMS = "params";
  private static final String LABEL = "label";
  private static final String VALUE = "value";
  private static final String SET = "set";
  private static final String EXPERIMENT = "experiment";
  private static final String YML = ".yml";
  private static final Set<String> KEYS = Set.of(STAGE, BASE, MODE, PARAMS);
  private static final Pattern STAGE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");
  private static final Pattern LABEL_NAME = Pattern.compile("[a-z0-9][a-z0-9.-]*");
  private static final Pattern SEGMENT = Pattern.compile("[a-z][a-z0-9_]*");
  private static final Pattern FILE = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*\\.yml");

  /** Layout under the nexussync root: {@code calibration/{thresholds.yml, gold/}}. */
  static final String CALIBRATION = "calibration";

  static final String THRESHOLDS = "thresholds.yml";
  static final String GOLD = "gold";

  /** Metrics of the report table, in column order. */
  static final List<String> REPORTED =
      List.of(
          Metrics.GOLD_VIOLATION,
          Metrics.FAIRNESS_GAP,
          Metrics.GOLD_HIT,
          Metrics.CALLS_PER_RUN,
          Metrics.STABILITY,
          Metrics.F1,
          Metrics.HALLUCINATION,
          Metrics.CHOSEN_TOP3,
          Metrics.TRUTH_ALIGNMENT);

  private static final Logger LOG = LoggerFactory.getLogger(Sweep.class);
  private static final YAMLMapper YAML =
      YAMLMapper.builder()
          .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
          .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
          .build();

  private Sweep() {}

  /** Runs one generated experiment with one seed; the CLI binds executor, couples and output. */
  @FunctionalInterface
  public interface Runner {

    /**
     * Runs the experiment.
     *
     * @param experiment generated experiment file
     * @param seed seed of this pass
     * @return the records of every couple and simulated date
     * @throws NexussyncException if a run fails
     */
    List<RunRecord> run(Path experiment, long seed) throws NexussyncException;
  }

  /**
   * Expands {@code matrix} into {@code paramsDir}.
   *
   * @param matrix the matrix YAML
   * @param paramsDir the {@code params/} directory: holds {@code default.yml} and the base, and
   *     receives the generated files (overwritten when they exist)
   * @return the generated files, in matrix order (parameter, then value)
   * @throws ParamsException if the matrix is unreadable or malformed, a value is invalid for {@code
   *     ParamSpec}, two values share a name or a file cannot be written
   * @implNote O(v · p) time and space, v values, p size of the parameter set.
   */
  public static List<Path> expand(Path matrix, Path paramsDir) throws ParamsException {
    ObjectNode root = readMatrix(matrix);
    String stage = stageOf(root, matrix);
    ObjectNode base = base(root, paramsDir, matrix);
    Map<String, ObjectNode> files = new LinkedHashMap<>();
    for (Variant v : variants(root, matrix)) {
      String name = PREFIX + stage + "-" + v.name();
      if (files.containsKey(name)) {
        throw new ParamsException("duplicate sweep value " + name + " in " + matrix);
      }
      files.put(name, experiment(name, base, v));
    }
    Map<Path, String> texts = new LinkedHashMap<>();
    for (Map.Entry<String, ObjectNode> f : files.entrySet()) {
      String text = header(matrix, stage) + write(f.getValue());
      validate(text, paramsDir.resolve(DEFAULTS), f.getKey());
      texts.put(paramsDir.resolve(f.getKey() + YML), text);
    }
    return writeAll(texts);
  }

  /**
   * Reads the stage of a matrix.
   *
   * @param matrix the matrix YAML
   * @return its {@code stage}
   * @throws ParamsException if the matrix is unreadable or its stage is missing or malformed
   * @implNote O(size of the matrix) time and space.
   */
  public static String stage(Path matrix) throws ParamsException {
    return stageOf(readMatrix(matrix), matrix);
  }

  /**
   * Writes {@code calibrationDir/report-<date>-<stage>.md}: one row per experiment with the metrics
   * of the selection rule, the winner of {@link Selector#pick(List, Thresholds)}, the ranking of
   * the survivors and the trace of discards.
   *
   * @param calibrationDir the {@code calibration/} directory, created if missing
   * @param stage stage of the matrix
   * @param date date in the name and title of the report
   * @param results measured experiments, in matrix order
   * @param th calibration thresholds
   * @return the written report
   * @throws ParamsException if the report cannot be written
   * @implNote O(n log n + n · m) time and O(n · m) space, n experiments, m reported metrics.
   */
  public static Path report(
      Path calibrationDir,
      String stage,
      LocalDate date,
      List<ExperimentResult> results,
      Thresholds th)
      throws ParamsException {
    StringBuilder md = new StringBuilder();
    md.append("# Calibración — etapa ").append(stage).append(" — ").append(date).append("\n\n");
    md.append("Regla (`Selector`, modo ").append(SelectorMode.MEAN).append("): descartar ");
    md.append("`goldViolationRate` > ").append(Comparer.format(th.goldViolationRateMax()));
    md.append(", descartar `fairnessGap` > ").append(Comparer.format(th.fairnessGapMax()));
    md.append(", máximo `goldHitRate`, desempate por mínimo `callsPerRun`.");
    md.append(" Una métrica filtrada sin datos (n/a) descarta.\n\n");
    table(md, results);
    selection(md, results, th);
    md.append('\n').append(Comparer.X5_NOTE).append('\n');
    Path out = calibrationDir.resolve("report-" + date + "-" + stage + ".md");
    try {
      Files.createDirectories(calibrationDir);
      Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new ParamsException("cannot write " + out, e);
    }
    return out;
  }

  private static void table(StringBuilder md, List<ExperimentResult> results) {
    md.append("| Experimento | runs |");
    REPORTED.forEach(m -> md.append(' ').append(m).append(" |"));
    md.append("\n|---|---|");
    REPORTED.forEach(m -> md.append("---|"));
    md.append('\n');
    for (ExperimentResult r : results) {
      md.append("| ").append(r.experiment()).append(" | ").append(r.runs()).append(" |");
      REPORTED.forEach(m -> md.append(' ').append(Comparer.format(r.metric(m))).append(" |"));
      md.append('\n');
    }
  }

  private static void selection(StringBuilder md, List<ExperimentResult> results, Thresholds th) {
    Optional<Selection> pick = Selector.pick(results, th);
    md.append("\n## Ganador\n\n");
    md.append(
        pick.map(s -> "`" + s.winner().experiment() + "`").orElse("ninguno: todas descartadas"));
    md.append("\n\n## Ranking de supervivientes\n\n");
    List<ExperimentResult> ranking = pick.map(Selection::ranking).orElse(List.of());
    for (int i = 0; i < ranking.size(); i++) {
      md.append(i + 1).append(". `").append(ranking.get(i).experiment()).append("`\n");
    }
    md.append("\n## Descartes\n\n");
    List<Selection.Discard> discards = Selector.discards(results, th);
    if (discards.isEmpty()) {
      md.append("ninguno\n");
    }
    for (Selection.Discard d : discards) {
      md.append("- `").append(d.experiment()).append("`: ").append(d.reason());
      md.append(" = ").append(Comparer.format(d.value()));
      md.append(" > ").append(Comparer.format(d.limit())).append('\n');
    }
  }

  /**
   * Runs a sweep: loads {@code calibration/thresholds.yml} and {@code calibration/gold/}, expands
   * {@code matrix} into {@code dir/params/}, runs every generated experiment once per seed through
   * {@code runner}, measures all its runs together with {@link Metrics#of(List, Map)} and writes
   * the {@link #report}.
   *
   * @param matrix the matrix YAML
   * @param dir the nexussync root
   * @param seeds seeds of every experiment
   * @param date date of the report
   * @param runner runs one experiment file with one seed
   * @return the written report
   * @throws NexussyncException if the thresholds, gold set or matrix are invalid, or a run fails
   * @implNote O(e · s · cost of one runner call) time, e experiments, s seeds; O(runs of one
   *     experiment) records in memory.
   */
  public static Path run(Path matrix, Path dir, List<Long> seeds, LocalDate date, Runner runner)
      throws NexussyncException {
    Path calibration = dir.resolve(CALIBRATION);
    Thresholds th = Thresholds.load(calibration.resolve(THRESHOLDS));
    Map<String, GoldEntry> gold = gold(calibration.resolve(GOLD));
    List<ExperimentResult> results = new ArrayList<>();
    for (Path file : expand(matrix, dir.resolve(PARAMS))) {
      List<RunRecord> records = new ArrayList<>();
      for (long seed : seeds) {
        records.addAll(runner.run(file, seed));
      }
      String name = String.valueOf(file.getFileName()).replaceFirst("\\.yml$", "");
      results.add(new ExperimentResult(name, records.size(), Metrics.of(records, gold)));
    }
    return report(calibration, stage(matrix), date, results, th);
  }

  /**
   * The gold set of a sweep.
   *
   * @param goldDir the {@code calibration/gold/} directory
   * @return its entries by {@code coupleId}; none (and a warning: gold metrics will be NaN) when
   *     the directory does not exist
   * @throws ParamsException if a gold file is invalid
   * @implNote O(cost of {@link GoldSet#load(Path)}).
   */
  public static Map<String, GoldEntry> gold(Path goldDir) throws ParamsException {
    if (!Files.isDirectory(goldDir)) {
      LOG.warn("no gold set in {}: gold metrics will be n/a", goldDir);
      return Map.of();
    }
    return GoldSet.load(goldDir);
  }

  private static ObjectNode readMatrix(Path matrix) throws ParamsException {
    ObjectNode root = readObject(matrix);
    for (Iterator<String> it = root.fieldNames(); it.hasNext(); ) {
      String key = it.next();
      if (!KEYS.contains(key)) {
        throw new ParamsException("unknown matrix key '" + key + "' in " + matrix);
      }
    }
    if (!ONE_AT_A_TIME.equals(root.path(MODE).asText())) {
      throw new ParamsException("matrix mode must be " + ONE_AT_A_TIME + " in " + matrix);
    }
    return root;
  }

  private static String stageOf(ObjectNode root, Path matrix) throws ParamsException {
    String stage = root.path(STAGE).asText();
    if (!root.path(STAGE).isTextual() || !STAGE_NAME.matcher(stage).matches()) {
      throw new ParamsException("matrix stage must match " + STAGE_NAME + " in " + matrix);
    }
    return stage;
  }

  /** The base experiment tree: empty for {@code default.yml}, else the base file without name. */
  private static ObjectNode base(ObjectNode root, Path paramsDir, Path matrix)
      throws ParamsException {
    String base = root.path(BASE).asText();
    if (!root.path(BASE).isTextual() || !FILE.matcher(base).matches()) {
      throw new ParamsException("matrix base must be a .yml file of params/ in " + matrix);
    }
    if (DEFAULTS.equals(base)) {
      return JsonNodeFactory.instance.objectNode();
    }
    ObjectNode tree = readObject(paramsDir.resolve(base));
    tree.remove(EXPERIMENT);
    return tree;
  }

  private static List<Variant> variants(ObjectNode root, Path matrix) throws ParamsException {
    if (!(root.get(PARAMS) instanceof ObjectNode params) || params.isEmpty()) {
      throw new ParamsException("matrix params must be a non-empty mapping in " + matrix);
    }
    List<Variant> out = new ArrayList<>();
    for (Map.Entry<String, JsonNode> e : params.properties()) {
      String key = e.getKey();
      if (!isPath(key) || !e.getValue().isArray() || e.getValue().isEmpty()) {
        throw new ParamsException(
            "matrix param '" + key + "' must be a snake_case path with a non-empty list");
      }
      for (JsonNode item : e.getValue()) {
        out.add(variant(key, item));
      }
    }
    return out;
  }

  private static Variant variant(String key, JsonNode item) throws ParamsException {
    if (item.isValueNode() && !item.isNull()) {
      return new Variant(key, label(item.asText()), Map.of(key, item));
    }
    if (!(item instanceof ObjectNode o)
        || !o.path(LABEL).isTextual()
        || o.has(VALUE) == o.has(SET)
        || o.size() != 2) {
      throw new ParamsException(
          "matrix value of '" + key + "' must be a scalar, {label, value} or {label, set}");
    }
    String label = label(o.get(LABEL).asText());
    if (o.has(VALUE)) {
      return new Variant(key, label, Map.of(key, o.get(VALUE)));
    }
    return new Variant(key, label, sets(key, o.get(SET)));
  }

  private static Map<String, JsonNode> sets(String key, JsonNode set) throws ParamsException {
    if (!(set instanceof ObjectNode o) || o.isEmpty()) {
      throw new ParamsException("matrix set of '" + key + "' must be a non-empty mapping");
    }
    Map<String, JsonNode> out = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> e : o.properties()) {
      if (!isPath(e.getKey())) {
        throw new ParamsException("matrix set of '" + key + "' has a bad path: " + e.getKey());
      }
      out.put(e.getKey(), e.getValue());
    }
    return out;
  }

  /** A dotted snake_case YAML path: segments split on {@code .}, each {@code [a-z][a-z0-9_]*}. */
  private static boolean isPath(String key) {
    for (String segment : key.split("\\.", -1)) {
      if (!SEGMENT.matcher(segment).matches()) {
        return false;
      }
    }
    return true;
  }

  private static String label(String raw) throws ParamsException {
    String label = raw.toLowerCase(Locale.ROOT).replace('_', '-');
    if (!LABEL_NAME.matcher(label).matches()) {
      throw new ParamsException("matrix value label must match " + LABEL_NAME + ": " + raw);
    }
    return label;
  }

  private static ObjectNode experiment(String name, ObjectNode base, Variant v)
      throws ParamsException {
    ObjectNode out = JsonNodeFactory.instance.objectNode();
    out.put(EXPERIMENT, name);
    out.setAll(base.deepCopy());
    for (Map.Entry<String, JsonNode> s : v.sets().entrySet()) {
      put(out, s.getKey(), s.getValue());
    }
    return out;
  }

  /** Sets {@code value} at the dotted {@code path}, creating the missing mappings. */
  private static void put(ObjectNode root, String path, JsonNode value) throws ParamsException {
    String[] parts = path.split("\\.");
    ObjectNode node = root;
    for (int i = 0; i < parts.length - 1; i++) {
      JsonNode child = node.get(parts[i]);
      if (child == null) {
        node = node.putObject(parts[i]);
      } else if (child instanceof ObjectNode o) {
        node = o;
      } else {
        throw new ParamsException("'" + path + "' crosses a non-mapping value of the base");
      }
    }
    node.set(parts[parts.length - 1], value.deepCopy());
  }

  private static String header(Path matrix, String stage) {
    return "# Generado por Sweep desde "
        + matrix.getFileName()
        + " (etapa "
        + stage
        + "); no editar a mano: se regenera con `sweep`.\n";
  }

  private static String write(ObjectNode tree) throws ParamsException {
    try {
      return YAML.writeValueAsString(tree);
    } catch (JsonProcessingException e) {
      throw new ParamsException("cannot render experiment yaml", e);
    }
  }

  /** Loads the text as an experiment on top of the defaults, through a temporary file. */
  private static void validate(String text, Path defaults, String name) throws ParamsException {
    Path tmp;
    try {
      tmp = Files.createTempFile("nexussync-sweep", YML);
    } catch (IOException e) {
      throw new ParamsException("cannot validate " + name, e);
    }
    try {
      Files.writeString(tmp, text, StandardCharsets.UTF_8);
      ParamsLoader.load(tmp, defaults);
    } catch (ParamsException e) {
      throw new ParamsException(name + ": " + e.getMessage(), e);
    } catch (IOException e) {
      throw new ParamsException("cannot validate " + name, e);
    } finally {
      deleteQuietly(tmp);
    }
  }

  private static List<Path> writeAll(Map<Path, String> texts) throws ParamsException {
    for (Map.Entry<Path, String> t : texts.entrySet()) {
      try {
        Files.writeString(t.getKey(), t.getValue(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new ParamsException("cannot write " + t.getKey(), e);
      }
    }
    return List.copyOf(texts.keySet());
  }

  private static ObjectNode readObject(Path file) throws ParamsException {
    try {
      if (YAML.readTree(file.toFile()) instanceof ObjectNode o) {
        return o;
      }
    } catch (IOException e) {
      throw new ParamsException("cannot read " + file + ": " + e.getMessage(), e);
    }
    throw new ParamsException(file + " is not a YAML mapping");
  }

  private static void deleteQuietly(Path tmp) {
    try {
      Files.deleteIfExists(tmp);
    } catch (IOException e) {
      LOG.warn("cannot delete {}", tmp, e);
    }
  }

  /** One value of the matrix: its key, its label and the YAML paths it sets. */
  private record Variant(String key, String label, Map<String, JsonNode> sets) {

    String name() {
      return key.replace('.', '-').replace('_', '-') + "-" + label;
    }
  }
}
