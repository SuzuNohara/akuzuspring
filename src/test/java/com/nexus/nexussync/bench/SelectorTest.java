package com.nexus.nexussync.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests of the selection rule of a calibration stage (U13-08). */
class SelectorTest {

  private static final Thresholds TH = new Thresholds(0.0, 0.15, 0.70, 0.02, 0.40, 0.50, 0.60);

  // U13-08
  @Test
  void given_mixed_results_when_pick_then_filters_in_order_max_hit_and_trace() {
    ExperimentResult violates = result("violates", 0.1, 0.0, 0.99, 1.0);
    ExperimentResult unfair = result("unfair", 0.0, 0.2, 0.95, 1.0);
    ExperimentResult good = result("good", 0.0, 0.1, 0.8, 5.0);
    ExperimentResult best = result("best", 0.0, 0.15, 0.9, 9.0);

    Optional<Selection> s = Selector.pick(List.of(violates, unfair, good, best), TH);

    assertThat(s).isPresent();
    assertThat(s.get().winner()).isEqualTo(best);
    assertThat(s.get().ranking()).containsExactly(best, good);
    assertThat(s.get().discards())
        .containsExactly(
            new Selection.Discard("violates", Selection.Reason.GOLD_VIOLATION, 0.1, 0.0),
            new Selection.Discard("unfair", Selection.Reason.FAIRNESS_GAP, 0.2, 0.15));
  }

  // U13-08
  @Test
  void given_violation_and_unfair_same_experiment_when_pick_then_violation_reported_first() {
    ExperimentResult both = result("both", 0.5, 0.9, 0.9, 1.0);

    assertThat(Selector.discards(List.of(both), TH))
        .extracting(Selection.Discard::reason)
        .containsExactly(Selection.Reason.GOLD_VIOLATION);
  }

  // U13-08
  @Test
  void given_equal_hit_rate_when_pick_then_fewest_calls_then_name() {
    ExperimentResult costly = result("a-costly", 0.0, 0.0, 0.8, 5.0);
    ExperimentResult cheap = result("z-cheap", 0.0, 0.0, 0.8, 3.0);
    ExperimentResult cheapTwin = result("y-cheap", 0.0, 0.0, 0.8, 3.0);

    Selection s = Selector.pick(List.of(costly, cheap, cheapTwin), TH).orElseThrow();

    assertThat(s.ranking()).containsExactly(cheapTwin, cheap, costly);
  }

  // U13-08
  @Test
  void given_no_candidates_when_pick_then_empty_but_trace_available() {
    ExperimentResult violates = result("violates", 0.2, 0.0, 1.0, 1.0);

    assertThat(Selector.pick(List.of(), TH)).isEmpty();
    assertThat(Selector.pick(List.of(violates), TH)).isEmpty();
    assertThat(Selector.discards(List.of(violates), TH)).hasSize(1);
  }

  @Test
  void given_nan_metrics_when_pick_then_unmeasured_filters_discard_and_nan_ranks_last() {
    ExperimentResult noGold = result("no-gold", Double.NaN, 0.0, 0.9, 1.0);
    ExperimentResult noFair = result("no-fair", 0.0, Double.NaN, 0.9, 1.0);
    ExperimentResult noHit = result("no-hit", 0.0, 0.0, Double.NaN, 1.0);
    ExperimentResult noCalls = result("no-calls", 0.0, 0.0, 0.5, Double.NaN);
    ExperimentResult calls = result("calls", 0.0, 0.0, 0.5, 2.0);

    Selection s = Selector.pick(List.of(noGold, noFair, noHit, noCalls, calls), TH).orElseThrow();

    assertThat(s.ranking()).containsExactly(calls, noCalls, noHit);
    assertThat(s.discards())
        .extracting(Selection.Discard::experiment)
        .containsExactly("no-gold", "no-fair");
  }

  @Test
  void given_ci_lower_mode_when_pick_then_unsupported() {
    List<ExperimentResult> results = List.of(result("x", 0.0, 0.0, 1.0, 1.0));

    assertThatThrownBy(() -> Selector.pick(results, TH, SelectorMode.CI_LOWER))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(Selector.pick(results, TH, SelectorMode.MEAN)).isPresent();
    assertThat(SelectorMode.values()).containsExactly(SelectorMode.MEAN, SelectorMode.CI_LOWER);
  }

  @Test
  void given_records_when_built_then_checked_and_immutable() {
    Map<String, Double> m = new LinkedHashMap<>();
    m.put(Metrics.GOLD_HIT, 0.5);
    ExperimentResult r = new ExperimentResult("r", 2, m);
    m.put(Metrics.GOLD_HIT, 0.9);

    assertThat(r.metric(Metrics.GOLD_HIT)).isEqualTo(0.5);
    assertThat(r.metric("absent")).isNaN();
    assertThatThrownBy(() -> r.metrics().put("x", 1.0))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new ExperimentResult("r", -1, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    ExperimentResult other = result("o", 0.0, 0.0, 0.0, 0.0);
    assertThatThrownBy(() -> new Selection(r, List.of(other), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Selection(r, List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Selection.Discard("x", null, 0.0, 0.0))
        .isInstanceOf(NullPointerException.class);
  }

  private static ExperimentResult result(
      String name, double violation, double gap, double hit, double calls) {
    Map<String, Double> m = new LinkedHashMap<>();
    m.put(Metrics.GOLD_VIOLATION, violation);
    m.put(Metrics.FAIRNESS_GAP, gap);
    m.put(Metrics.GOLD_HIT, hit);
    m.put(Metrics.CALLS_PER_RUN, calls);
    return new ExperimentResult(name, 3, m);
  }
}
