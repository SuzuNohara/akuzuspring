package com.nexus.nexussync.params;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Canonical form and stable hash of the parameter set (T-10). */
final class ParamsHashTest {

  private static final String HEX8 = "^[0-9a-f]{8}$";

  // U1-07
  @Test
  void given_sameParamsBuiltTwice_when_hash_then_sameEightHexDigits() {
    String first = ParamsRecordsTest.sample().hash();
    String second = ParamsRecordsTest.sample().hash();

    assertThat(first).matches(HEX8).isEqualTo(second);
  }

  // U1-07
  @Test
  void given_baselineLoadedTwice_when_hash_then_stableAndDifferentFromDefaults() throws Exception {
    Path defaults = ParamsLoaderTest.defaults();
    Path baseline = defaults.resolveSibling("baseline-haiku.yml");

    String once = ParamsLoader.load(baseline, defaults).hash();
    String again = ParamsLoader.load(baseline, defaults).hash();
    String plain = ParamsLoader.load(defaults, defaults).hash();

    assertThat(once).matches(HEX8).isEqualTo(again).isNotEqualTo(plain);
  }

  @Test
  void given_oneScalarChanged_when_hash_then_differs() {
    Params base = ParamsRecordsTest.sample();
    Params other = copy(base, base.seed() + 1, base.sampler(), base.runtime());

    assertThat(other.hash()).isNotEqualTo(base.hash());
  }

  // D-18
  @Test
  void given_onlyRuntimeDiffers_when_hash_then_sameHash() {
    Params base = ParamsRecordsTest.sample();
    RuntimeParams elsewhere =
        new RuntimeParams(
            ExecutorKind.REPLAY,
            Path.of("/srv/other-checkout/nexussync"),
            Path.of("/usr/local/bin/arkannie"),
            Optional.of(Path.of("runs/replay")),
            5,
            "9.9.9");
    Params other = copy(base, base.seed(), base.sampler(), elsewhere);

    assertThat(other).isNotEqualTo(base);
    assertThat(other.hash()).isEqualTo(base.hash());
    assertThat(CanonicalYaml.canonical(other, Params.HASH_EXCLUDED))
        .doesNotContain("\"runtime\"")
        .doesNotContain("other-checkout");
    assertThat(CanonicalYaml.canonical(other)).contains("\"runtime\":{");
  }

  // D-18
  @Test
  void given_samplerEtaChanged_when_hash_then_stillDiffers() {
    Params base = ParamsRecordsTest.sample();
    SamplerParams s = base.sampler();
    SamplerParams tuned =
        new SamplerParams(
            s.sampleSize(),
            s.sampleMin(),
            s.radiusKm(),
            s.cooldownDays(),
            s.maxPerType(),
            s.homeShareMin(),
            s.rainMax(),
            s.weightsInit(),
            s.learningMethod(),
            s.eta() + 0.1,
            s.explorationMethod(),
            s.eps0(),
            s.tau(),
            s.emotionEnabled(),
            s.emotionWeight(),
            s.relaxOrder(),
            s.maxRelaxations());
    Params other = copy(base, base.seed(), tuned, base.runtime());

    assertThat(other.hash()).isNotEqualTo(base.hash());
  }

  @Test
  void given_excludedKeyNestedOnly_when_canonical_then_kept() {
    Map<String, Object> outer = new LinkedHashMap<>();
    outer.put("inner", Map.of("runtime", 1));
    outer.put("runtime", 2);

    assertThat(CanonicalYaml.canonical(outer, Set.of("runtime")))
        .isEqualTo("{\"inner\":{\"runtime\":1}}");
    assertThat(CanonicalYaml.canonical("scalar", Set.of("runtime"))).isEqualTo("\"scalar\"");
  }

  @Test
  void given_record_when_canonical_then_sortedSnakeKeysWithoutWhitespace() {
    AgentsParams agents = new AgentsParams("haiku", 90, "haiku", 120, MediatorClimate.AGGREGATED);

    assertThat(CanonicalYaml.canonical(agents))
        .isEqualTo(
            "{\"mediator_climate\":\"AGGREGATED\",\"mediator_model\":\"haiku\","
                + "\"mediator_timeout\":120,\"persona_model\":\"haiku\",\"persona_timeout\":90}");
  }

  @Test
  void given_mapInsertedOutOfOrder_when_canonical_then_entriesSortedByKey() {
    Map<String, Integer> map = new LinkedHashMap<>();
    map.put("zeta", 1);
    map.put("alpha", 2);
    map.put("mid", 3);

    assertThat(CanonicalYaml.canonical(map)).isEqualTo("{\"alpha\":2,\"mid\":3,\"zeta\":1}");
  }

  @Test
  void given_runtime_when_canonical_then_pathsArePlainStringsAndEmptyOptionalIsNull() {
    RuntimeParams runtime = ParamsRecordsTest.runtime(Path.of("/tmp/nexussync"));

    String canonical = CanonicalYaml.canonical(runtime);

    assertThat(canonical)
        .contains("\"arkannie_bin\":\"arkannie/bin/arkannie\"")
        .contains("\"nexussync_dir\":\"/tmp/nexussync\"")
        .contains("\"replay_dir\":null")
        .doesNotContain("file:");
  }

  @Test
  void given_fullParams_when_canonical_then_topLevelKeysSortedAndRenamedKeysKept() {
    String canonical = CanonicalYaml.canonical(ParamsRecordsTest.sample());

    int agents = canonical.indexOf("\"agents\":");
    int bench = canonical.indexOf("\"bench\":");
    int sampler = canonical.indexOf("\"sampler\":");
    assertThat(canonical).startsWith("{\"agents\":");
    assertThat(agents).isLessThan(bench);
    assertThat(bench).isLessThan(sampler);
    assertThat(canonical).contains("\"n_sample\":40").contains("\"w_min\":0.02");
    assertThat(canonical).doesNotContain(" ").doesNotContain("\n").doesNotContain("#");
  }

  @Test
  void given_knownText_when_digest_then_sha256Hex() {
    assertThat(CanonicalYaml.digest(""))
        .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    assertThat(CanonicalYaml.hash(Map.of())).isEqualTo(CanonicalYaml.digest("{}").substring(0, 8));
  }

  @Test
  void given_unserializableObject_when_canonical_then_illegalState() {
    Object bean = new Object();

    assertThatThrownBy(() -> CanonicalYaml.canonical(bean))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Object");
  }

  private static Params copy(Params b, long seed, SamplerParams sampler, RuntimeParams runtime) {
    return new Params(
        b.experiment(),
        seed,
        b.catalogDir(),
        b.placesCsv(),
        sampler,
        b.rounds(),
        b.agents(),
        b.decision(),
        b.place(),
        b.learning(),
        b.context(),
        runtime,
        b.rubric(),
        b.bench());
  }
}
