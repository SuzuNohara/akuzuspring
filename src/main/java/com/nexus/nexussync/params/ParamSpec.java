package com.nexus.nexussync.params;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Range table and structural rules of the parameter set.
 *
 * <p>{@link #RANGES} holds one {@link Range} per numeric component of the records of this package
 * (except {@code seed}), keyed by its YAML path. {@link #validate(Params)} walks the records by
 * reflection, so a numeric component added without a range fails fast, and then checks that {@code
 * decision.rank_points} has three entries and that the rubric criteria add up to 1 within {@value
 * #CRITERIA_TOLERANCE}. Enum names are validated earlier by Jackson.
 */
final class ParamSpec {

  static final double CRITERIA_TOLERANCE = 0.01;
  static final int RANK_POINTS_SIZE = 3;

  private static final Set<String> UNRANGED = Set.of("seed");
  private static final Range UNIT = new Range(0.0, 1.0);
  private static final Range RATE = new Range(0.0, 2.0);
  private static final Range DAYS = new Range(0.0, 365.0);
  private static final Range RADIUS_KM = new Range(0.1, 100.0);

  /** Declared after the shared ranges above: static initializers run in textual order. */
  static final Map<String, Range> RANGES = Collections.unmodifiableMap(ranges());

  private ParamSpec() {}

  /**
   * Validates every numeric value against {@link #RANGES} and the structural rules.
   *
   * @param params the parameter set to validate
   * @throws ParamsException if a value is absent, out of range, {@code rank_points} has not exactly
   *     three entries or the rubric criteria do not add up to 1
   * @implNote O(c) time, c = record components; O(d) space, d = nesting depth.
   */
  static void validate(Params params) throws ParamsException {
    walk(params, "");
    checkRankPoints(params.decision());
    checkCriteria(params.rubric());
  }

  /**
   * Checks one numeric value against its declared range.
   *
   * @param path YAML path of the value, e.g. {@code sampler.eta}
   * @param value the value read from the YAML
   * @throws ParamsException if the value is outside the range
   * @throws IllegalStateException if no range is declared for the path (programming error)
   * @implNote O(1) time and space.
   */
  static void checkRange(String path, Number value) throws ParamsException {
    Range range = RANGES.get(path);
    if (range == null) {
      throw new IllegalStateException("sin rango declarado: " + path);
    }
    if (!range.contains(value.doubleValue())) {
      throw new ParamsException(
          String.format(Locale.ROOT, "%s = %s fuera de %s", path, value, range));
    }
  }

  /**
   * Converts a camelCase component name to its snake_case YAML key, as Jackson does.
   *
   * @param camel the Java name, e.g. {@code decisionTtlHours}
   * @return the YAML key, e.g. {@code decision_ttl_hours}
   * @implNote O(n) time and space in the length of the name.
   */
  static String snake(String camel) {
    StringBuilder out = new StringBuilder(camel.length() + 4);
    for (int i = 0; i < camel.length(); i++) {
      char ch = camel.charAt(i);
      if (Character.isUpperCase(ch)) {
        out.append('_').append(Character.toLowerCase(ch));
      } else {
        out.append(ch);
      }
    }
    return out.toString();
  }

  private static void walk(Record record, String prefix) throws ParamsException {
    for (RecordComponent component : record.getClass().getRecordComponents()) {
      String path = prefix + yamlKey(component);
      Object value = read(record, component);
      if (value == null) {
        throw new ParamsException(path + ": valor ausente");
      }
      if (value instanceof Record nested) {
        walk(nested, path + ".");
      } else if (value instanceof Number number && !UNRANGED.contains(path)) {
        checkRange(path, number);
      }
    }
  }

  /**
   * YAML key of a record component: the explicit {@code JsonProperty} name when the design name was
   * renamed (e.g. {@code n_sample} for {@code sampleSize}), otherwise the snake_case of the Java
   * name.
   *
   * <p>The annotation is read from the accessor, not from the {@link RecordComponent}: Jackson's
   * {@code JsonProperty} has no {@code RECORD_COMPONENT} target, so the compiler propagates it to
   * the field, the accessor and the constructor parameter but never to the component itself.
   *
   * @param component the record component
   * @return the key Jackson uses for it in the YAML
   * @implNote O(n) time and space in the length of the name.
   */
  static String yamlKey(RecordComponent component) {
    JsonProperty explicit = component.getAccessor().getAnnotation(JsonProperty.class);
    return explicit == null ? snake(component.getName()) : explicit.value();
  }

  private static Object read(Record record, RecordComponent component) {
    try {
      return component.getAccessor().invoke(record);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("no se puede leer " + component.getName(), e);
    }
  }

  private static void checkRankPoints(DecisionParams decision) throws ParamsException {
    int size = decision.rankPoints().size();
    if (size != RANK_POINTS_SIZE) {
      throw new ParamsException(
          "decision.rank_points debe tener " + RANK_POINTS_SIZE + " valores, tiene " + size);
    }
  }

  private static void checkCriteria(RubricParams rubric) throws ParamsException {
    double sum = 0.0;
    for (double weight : rubric.criteria().values()) {
      sum += weight;
    }
    if (Math.abs(sum - 1.0) > CRITERIA_TOLERANCE) {
      throw new ParamsException(
          String.format(
              Locale.ROOT,
              "rubric.criteria suma %s, debe sumar 1 ± %s",
              new java.math.BigDecimal(sum)
                  .setScale(4, java.math.RoundingMode.HALF_UP)
                  .stripTrailingZeros()
                  .toPlainString(),
              CRITERIA_TOLERANCE));
    }
  }

  private static Map<String, Range> ranges() {
    Map<String, Range> r = new LinkedHashMap<>();
    sampler(r);
    r.put("rounds.k_pick", new Range(1.0, 100.0));
    r.put("rounds.k_vote", new Range(1.0, 100.0));
    r.put("rounds.final_size", new Range(1.0, 20.0));
    r.put("rounds.threshold_r1", new Range(0.0, 20.0));
    r.put("rounds.max_rounds", new Range(1.0, 5.0));
    r.put("agents.persona_timeout", new Range(1.0, 3600.0));
    r.put("agents.mediator_timeout", new Range(1.0, 3600.0));
    r.put("decision.more_options_batch", new Range(1.0, 50.0));
    r.put("decision.decision_ttl_hours", new Range(1.0, 720.0));
    r.put("decision.expired_weight", UNIT);
    r.put("decision.partial_weight", UNIT);
    r.put("decision.reject_block_days", DAYS);
    r.put("place.radius_km", RADIUS_KM);
    r.put("place.places_k", new Range(1.0, 20.0));
    r.put("learning.eta", RATE);
    r.put("learning.eta_pos", RATE);
    r.put("learning.eta_neg", RATE);
    r.put("learning.cold_start_min", new Range(0.0, 100.0));
    r.put("learning.w_min", UNIT);
    r.put("learning.w_max", UNIT);
    r.put("context.emotion_window_days", new Range(1.0, 365.0));
    r.put("context.history_window_days", new Range(1.0, 3650.0));
    r.put("runtime.max_calls", new Range(0.0, 100000.0));
    r.put("bench.truth_noise", new Range(0.0, 10.0));
    return r;
  }

  private static void sampler(Map<String, Range> r) {
    r.put("sampler.n_sample", new Range(1.0, 500.0));
    r.put("sampler.s_min", new Range(1.0, 500.0));
    r.put("sampler.radius_km", RADIUS_KM);
    r.put("sampler.cooldown_days", DAYS);
    r.put("sampler.max_per_type", new Range(1.0, 100.0));
    r.put("sampler.home_share_min", UNIT);
    r.put("sampler.rain_max", UNIT);
    r.put("sampler.eta", RATE);
    r.put("sampler.eps0", UNIT);
    r.put("sampler.tau", new Range(0.0, 100.0));
    r.put("sampler.emotion_weight", new Range(0.0, 5.0));
    r.put("sampler.max_relaxations", new Range(0.0, 8.0));
  }
}
