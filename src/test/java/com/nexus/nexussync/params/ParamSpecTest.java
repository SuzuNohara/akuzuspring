package com.nexus.nexussync.params;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Range table and validation of the parameter set (T-09). */
final class ParamSpecTest {

  // U1-04
  @Test
  void given_etaOutOfRange_when_load_then_messageHasPathAndRange() {
    assertThatThrownBy(
            () ->
                ParamsLoader.load(
                    ParamsLoaderTest.fixture("out-of-range.yml"), ParamsLoaderTest.defaults()))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("sampler.eta")
        .hasMessageContaining("5.0")
        .hasMessageContaining("[0, 2]");
  }

  // U1-05
  @Test
  void given_unknownEnumName_when_load_then_messageHasPathAndValue() {
    assertThatThrownBy(
            () ->
                ParamsLoader.load(
                    ParamsLoaderTest.fixture("bad-enum.yml"), ParamsLoaderTest.defaults()))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("sampler.learning_method")
        .hasMessageContaining("BOGUS");
  }

  // U1-06
  @Test
  void given_criteriaNotSummingOne_when_load_then_paramsException() {
    assertThatThrownBy(
            () ->
                ParamsLoader.load(
                    ParamsLoaderTest.fixture("bad-rubric.yml"), ParamsLoaderTest.defaults()))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("rubric.criteria")
        .hasMessageContaining("0.75");
  }

  // U1-08
  @Test
  void given_allRecords_when_reflected_then_everyNumericComponentHasRangeAndNothingElse() {
    List<String> numericPaths = new ArrayList<>();
    collectNumeric(Params.class, "", numericPaths);

    assertThat(numericPaths).contains("seed", "sampler.n_sample", "learning.w_min");
    numericPaths.remove("seed");
    assertThat(ParamSpec.RANGES.keySet()).containsExactlyInAnyOrderElementsOf(numericPaths);
    assertThat(ParamSpec.RANGES.values()).doesNotContainNull();
  }

  // U1-10
  @Test
  void given_twoRankPoints_when_load_then_paramsException() {
    assertThatThrownBy(
            () ->
                ParamsLoader.load(
                    ParamsLoaderTest.fixture("bad-rank.yml"), ParamsLoaderTest.defaults()))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("decision.rank_points")
        .hasMessageContaining("3");
  }

  @Test
  void given_validSample_when_validate_then_noException() {
    assertThatCode(() -> ParamSpec.validate(ParamsRecordsTest.sample())).doesNotThrowAnyException();
  }

  @Test
  void given_nullSection_when_validate_then_paramsExceptionNamesIt() {
    Params base = ParamsRecordsTest.sample();
    Params broken =
        new Params(
            base.experiment(),
            base.seed(),
            base.catalogDir(),
            base.placesCsv(),
            base.sampler(),
            base.rounds(),
            base.agents(),
            base.decision(),
            base.place(),
            base.learning(),
            base.context(),
            base.runtime(),
            base.rubric(),
            null);

    assertThatThrownBy(() -> ParamSpec.validate(broken))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("bench");
  }

  @Test
  void given_pathWithoutRange_when_checkRange_then_illegalState() {
    assertThatThrownBy(() -> ParamSpec.checkRange("nowhere.value", 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nowhere.value");
  }

  @Test
  void given_range_when_valueOnBounds_then_containedAndNanIsNot() {
    Range range = new Range(0.0, 2.0);

    assertThat(range.contains(0.0)).isTrue();
    assertThat(range.contains(2.0)).isTrue();
    assertThat(range.contains(1.0)).isTrue();
    assertThat(range.contains(-0.0001)).isFalse();
    assertThat(range.contains(2.0001)).isFalse();
    assertThat(range.contains(Double.NaN)).isFalse();
  }

  @Test
  void given_range_when_toString_then_compactBounds() {
    assertThat(new Range(0.0, 2.0)).hasToString("[0, 2]");
    assertThat(new Range(0.1, 100.0)).hasToString("[0.1, 100]");
    assertThat(new Range(0.02, 0.6)).hasToString("[0.02, 0.6]");
  }

  @Test
  void given_invertedBounds_when_constructed_then_illegalArgument() {
    assertThatThrownBy(() -> new Range(2.0, 1.0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void given_camelCase_when_snake_then_yamlKey() {
    assertThat(ParamSpec.snake("thresholdR1")).isEqualTo("threshold_r1");
    assertThat(ParamSpec.snake("eps0")).isEqualTo("eps0");
    assertThat(ParamSpec.snake("decisionTtlHours")).isEqualTo("decision_ttl_hours");
  }

  private static void collectNumeric(Class<?> type, String prefix, List<String> out) {
    for (RecordComponent c : type.getRecordComponents()) {
      String path = prefix + ParamSpec.yamlKey(c);
      Class<?> t = c.getType();
      if (t.isRecord()) {
        collectNumeric(t, path + ".", out);
      } else if (t == int.class || t == long.class || t == double.class) {
        out.add(path);
      }
    }
  }

  @Test
  void given_renamedComponent_when_yamlKey_then_explicitNameElseSnake() {
    RecordComponent[] sampler = SamplerParams.class.getRecordComponents();
    RecordComponent[] context = ContextParams.class.getRecordComponents();

    assertThat(ParamSpec.yamlKey(sampler[0])).isEqualTo("n_sample");
    assertThat(ParamSpec.yamlKey(sampler[1])).isEqualTo("s_min");
    assertThat(ParamSpec.yamlKey(context[0])).isEqualTo("emotion_window_days");
  }
}
