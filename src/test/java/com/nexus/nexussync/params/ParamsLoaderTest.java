package com.nexus.nexussync.params;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Loading and deep merge of experiment YAML files (T-08, T-11). */
final class ParamsLoaderTest {

  static final Path FIXTURES = Path.of("src/test/resources/nexussync/params");
  private static final ObjectMapper JSON = new ObjectMapper();

  /**
   * Resolves {@code nexussync/params/default.yml} from the {@code nexussync.dir} system property,
   * skipping the test when the directory is not available.
   *
   * @return the path of the real defaults file
   */
  static Path defaults() {
    String dir = System.getProperty("nexussync.dir", "");
    Path root = Path.of(dir);
    Assumptions.assumeTrue(
        !dir.isEmpty() && Files.isDirectory(root), "nexussync.dir no definido o inexistente");
    return root.resolve("params").resolve("default.yml");
  }

  static Path fixture(String name) {
    return FIXTURES.resolve(name);
  }

  // U1-01
  @Test
  void given_defaultsOnly_when_load_then_pipelineValuesAndResolvedRuntime() throws Exception {
    Path defaults = defaults();

    Params p = ParamsLoader.load(defaults, defaults);

    assertThat(p.experiment()).isEqualTo("default");
    assertThat(p.sampler().sampleSize()).isEqualTo(40);
    assertThat(p.sampler().sampleMin()).isEqualTo(15);
    assertThat(p.sampler().weightsInit()).hasSize(Feature.values().length).containsValues(1.0);
    assertThat(p.sampler().relaxOrder())
        .containsExactly(
            FilterName.COOLDOWN, FilterName.WEATHER, FilterName.BUDGET, FilterName.RADIUS);
    assertThat(p.rounds().pickCount()).isEqualTo(15);
    assertThat(p.rounds().allowTwoAi()).isTrue();
    assertThat(p.agents().mediatorClimate()).isEqualTo(MediatorClimate.AGGREGATED);
    assertThat(p.decision().rankPoints()).containsExactly(3, 2, 1);
    assertThat(p.place().relaxOrder())
        .containsExactly(PlaceRelax.RADIUS, PlaceRelax.HOURS_UNKNOWN, PlaceRelax.WEATHER);
    assertThat(p.learning().weightMin()).isEqualTo(0.02);
    assertThat(p.context().historyWindowDays()).isEqualTo(90);
    assertThat(p.runtime().executor()).isEqualTo(ExecutorKind.ARKANNIE);
    assertThat(p.runtime().nexussyncDir())
        .isEqualTo(defaults.toAbsolutePath().normalize().getParent().getParent());
    assertThat(p.runtime().arkannieBin()).isEqualTo(Path.of("arkannie/bin/arkannie"));
    assertThat(p.runtime().replayDir()).isEmpty();
    assertThat(p.runtime().maxCalls()).isEqualTo(200);
    assertThat(p.runtime().arkannieVersion()).isEqualTo("0.3.0");
    assertThat(p.bench().truthNoise()).isEqualTo(0.0);
  }

  // U1-02
  @Test
  void given_overrideEta_when_load_then_scalarsOverriddenAndRestKept() throws Exception {
    Params p = ParamsLoader.load(fixture("override-eta.yml"), defaults());

    assertThat(p.experiment()).isEqualTo("override-eta");
    assertThat(p.sampler().eta()).isEqualTo(0.9);
    assertThat(p.learning().eta()).isEqualTo(0.5);
    assertThat(p.sampler().sampleMin()).isEqualTo(15);
    assertThat(p.learning().etaPos()).isEqualTo(0.45);
  }

  // U1-03
  @Test
  void given_listReplace_when_load_then_listsReplacedNotConcatenated() throws Exception {
    Params p = ParamsLoader.load(fixture("list-replace.yml"), defaults());

    assertThat(p.sampler().relaxOrder()).containsExactly(FilterName.BUDGET);
    assertThat(p.decision().rankPoints()).containsExactly(5, 3, 1);
    assertThat(p.place().relaxOrder()).hasSize(3);
  }

  // U1-09
  @Test
  void given_unknownKey_when_load_then_paramsExceptionNamesThePath(@TempDir Path tmp)
      throws Exception {
    Path experiment = write(tmp, "sampler:\n  n_samples: 40\n");

    assertThatThrownBy(() -> ParamsLoader.load(experiment, defaults()))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("sampler")
        .hasMessageContaining("n_samples");
  }

  @Test
  void given_baselineHaiku_when_load_then_onlyExperimentAndSeedChange() throws Exception {
    Path defaults = defaults();
    Path baseline = defaults.resolveSibling("baseline-haiku.yml");

    Params p = ParamsLoader.load(baseline, defaults);

    assertThat(p.experiment()).isEqualTo("baseline-haiku");
    assertThat(p.seed()).isEqualTo(42L);
    assertThat(p.sampler()).isEqualTo(ParamsLoader.load(defaults, defaults).sampler());
  }

  @Test
  void given_nestedObjects_when_merge_then_deepMergedAndListsReplaced() throws IOException {
    JsonNode base =
        JSON.readTree("{\"a\":{\"x\":1,\"y\":[1,2],\"z\":{\"k\":true}},\"b\":2,\"c\":\"s\"}");
    JsonNode override =
        JSON.readTree("{\"a\":{\"x\":9,\"y\":[3],\"z\":7},\"c\":{\"n\":1},\"d\":4}");

    JsonNode merged = ParamsLoader.merge(base, override);

    assertThat(merged.at("/a/x").intValue()).isEqualTo(9);
    assertThat(merged.at("/a/y").size()).isEqualTo(1);
    assertThat(merged.at("/a/y/0").intValue()).isEqualTo(3);
    assertThat(merged.at("/a/z").intValue()).isEqualTo(7);
    assertThat(merged.at("/b").intValue()).isEqualTo(2);
    assertThat(merged.at("/c/n").intValue()).isEqualTo(1);
    assertThat(merged.at("/d").intValue()).isEqualTo(4);
    assertThat(base.at("/a/x").intValue()).as("base no se muta").isEqualTo(1);
  }

  @Test
  void given_explicitNexussyncDir_when_load_then_notOverwritten(@TempDir Path tmp)
      throws Exception {
    Path experiment = write(tmp, "runtime:\n  nexussync_dir: /opt/nexussync\n");

    Params p = ParamsLoader.load(experiment, defaults());

    assertThat(p.runtime().nexussyncDir()).isEqualTo(Path.of("/opt/nexussync"));
  }

  @Test
  void given_missingExperimentFile_when_load_then_paramsException(@TempDir Path tmp) {
    Path missing = tmp.resolve("missing.yml");

    assertThatThrownBy(() -> ParamsLoader.load(missing, defaults()))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("missing.yml");
  }

  @Test
  void given_emptyExperimentFile_when_load_then_paramsException(@TempDir Path tmp)
      throws Exception {
    Path empty = write(tmp, "");

    assertThatThrownBy(() -> ParamsLoader.load(empty, defaults()))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("mapa");
  }

  @Test
  void given_malformedYaml_when_load_then_paramsException(@TempDir Path tmp) throws Exception {
    Path broken = write(tmp, "sampler: [unclosed\n  eta: 1\n");

    assertThatThrownBy(() -> ParamsLoader.load(broken, defaults()))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("experiment.yml");
  }

  @Test
  void given_defaultsWithoutRuntimeSection_when_load_then_paramsException(@TempDir Path tmp)
      throws Exception {
    Path defaults = write(tmp, "experiment: bare\n");

    assertThatThrownBy(() -> ParamsLoader.load(defaults, defaults))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("runtime");
  }

  @Test
  void given_defaultsAtFilesystemRoot_when_load_then_paramsException() {
    Path root = Path.of("/default.yml");

    assertThatThrownBy(() -> ParamsLoader.load(root, root))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("nexussync_dir");
  }

  @Test
  void given_nonIntegerInList_when_load_then_pathCarriesTheIndex(@TempDir Path tmp)
      throws Exception {
    Path experiment = write(tmp, "decision:\n  rank_points: [3, two, 1]\n");

    assertThatThrownBy(() -> ParamsLoader.load(experiment, defaults()))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("decision.rank_points[1]");
  }

  private static Path write(Path dir, String yaml) throws IOException {
    Path file = dir.resolve("experiment.yml");
    Files.write(file, List.of(yaml.split("\n")), StandardCharsets.UTF_8);
    return file;
  }
}
