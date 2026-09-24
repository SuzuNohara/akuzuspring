package com.nexus.nexussync.specs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.nexus.nexussync.bench.GoldEntry;
import com.nexus.nexussync.bench.GoldSet;
import com.nexus.nexussync.bench.Thresholds;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Contract checks on the agent specifications, the calibration thresholds and the gold set draft
 * under {@code nexussync/} (Fase C, U13-07 and U13-01 on the real data).
 */
class SpecsTest {

  private static final YAMLMapper YAML = new YAMLMapper();
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String FENCE = "---\n";
  private static final Set<String> STATUSES = Set.of("draft", "candidate", "accepted");
  private static final String SEMVER = "\\d+\\.\\d+\\.\\d+";
  private static final String ACCEPTANCE = "\n## Criterios de aceptación\n";

  private static Path nexussyncDir() {
    Optional<String> dir = Optional.ofNullable(System.getProperty("nexussync.dir"));
    Assumptions.assumeTrue(dir.isPresent(), "nexussync.dir not set");
    Path root = Paths.get(dir.get());
    Assumptions.assumeTrue(Files.isDirectory(root), "nexussync dir missing: " + root);
    return root;
  }

  private static JsonNode frontMatter(Path spec) throws IOException {
    String text = Files.readString(spec, StandardCharsets.UTF_8);
    assertThat(text).as("front matter opening of " + spec).startsWith(FENCE);
    int end = text.indexOf("\n" + FENCE, FENCE.length() - 1);
    assertThat(end).as("front matter closing of " + spec).isPositive();
    return YAML.readTree(text.substring(FENCE.length(), end + 1));
  }

  // U13-07
  @ParameterizedTest
  @ValueSource(strings = {"pareja.md", "agente-nexussync.md"})
  void given_spec_when_readFrontMatter_then_versionStatusAndExistingHarness(String name)
      throws IOException {
    Path root = nexussyncDir();
    Path spec = root.resolve("specs").resolve(name);

    JsonNode fm = frontMatter(spec);

    assertThat(fm.path("version").asText()).matches(SEMVER);
    assertThat(STATUSES).contains(fm.path("status").asText());
    assertThat(fm.path("harness").isTextual()).isTrue();
    assertThat(root.resolve(fm.path("harness").asText())).isRegularFile();
    assertThat(Files.readString(spec, StandardCharsets.UTF_8)).contains(ACCEPTANCE);
  }

  // U13-07
  @ParameterizedTest
  @ValueSource(strings = {"pareja.md", "agente-nexussync.md"})
  void given_spec_when_readChangelog_then_itsVersionIsCited(String name) throws IOException {
    Path specs = nexussyncDir().resolve("specs");
    String version = frontMatter(specs.resolve(name)).path("version").asText();

    List<String> lines = Files.readAllLines(specs.resolve("CHANGELOG.md"), StandardCharsets.UTF_8);

    assertThat(lines).anyMatch(l -> l.contains("`" + name + "` " + version));
  }

  // U13-01
  @Test
  void given_goldDraft_when_load_then_oneEntryPerFixtureCoupleWithCatalogLabels() throws Exception {
    Path root = nexussyncDir();
    Path fixtures = root.resolve("fixtures");

    Map<String, GoldEntry> gold = GoldSet.load(root.resolve("calibration").resolve("gold"));

    assertThat(gold.keySet()).isEqualTo(coupleIds(fixtures.resolve("couples")));
    Set<String> types = new HashSet<>();
    Set<String> ids = new HashSet<>();
    catalog(fixtures.resolve("catalog-synthetic").resolve("activities.csv"), types, ids);
    for (GoldEntry e : gold.values()) {
      assertThat(types).as(e.coupleId()).containsAll(e.expectedTypes());
      assertThat(types).as(e.coupleId()).containsAll(e.forbiddenTypes());
      assertThat(ids).as(e.coupleId()).containsAll(e.forbiddenIds());
      assertThat(e.expectedTypes()).as(e.coupleId()).noneMatch(e.forbiddenTypes()::contains);
      assertThat(e.notes()).as(e.coupleId()).isNotBlank();
    }
  }

  // U13-07 (umbrales de C2, propuesta D-CAL-2)
  @Test
  void given_thresholdsProposal_when_load_then_valuesOfFaseC2() throws Exception {
    Thresholds t = Thresholds.load(nexussyncDir().resolve("calibration").resolve("thresholds.yml"));

    assertThat(t).isEqualTo(new Thresholds(0.0, 0.15, 0.70, 0.02, 0.40, 0.50, 0.60));
  }

  private static Set<String> coupleIds(Path couples) throws IOException {
    Set<String> out = new HashSet<>();
    try (Stream<Path> files = Files.list(couples)) {
      for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
        JsonNode c = JSON.readTree(f.toFile());
        int a = c.path("a").path("user_id").asInt();
        int b = c.path("b").path("user_id").asInt();
        out.add(Math.min(a, b) + "-" + Math.max(a, b));
      }
    }
    return out;
  }

  private static void catalog(Path csv, Set<String> types, Set<String> ids) throws IOException {
    CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
    try (Reader in = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
      for (CSVRecord r : format.parse(in)) {
        types.add(r.get("activity_type"));
        ids.add(r.get("activity_id"));
      }
    }
  }
}
