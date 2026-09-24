package com.nexus.nexussync.ann;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.CatalogLoader;
import com.nexus.nexussync.params.AgentsParams;
import com.nexus.nexussync.params.ExecutorKind;
import com.nexus.nexussync.params.MediatorClimate;
import com.nexus.nexussync.params.RuntimeParams;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The real {@code persona} and {@code mediador} agents of {@code nexussync/.agents/} against the
 * pinned arkannie 0.3.0 (T-41). Calls Claude: tagged {@code arkannie}, excluded from the default
 * build and run once by NOVA.
 */
@Tag("arkannie")
class ArkannieAgentsTest {

  private static final int SAMPLE_SIZE = 15;
  private static final int K = 5;
  private static final String RUN_ID = "arkannie-agents-test-pick";
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path nexussyncDir() {
    String dir = System.getProperty("nexussync.dir", "");
    Path nx = Path.of(dir.isEmpty() ? "no-such-dir" : dir).toAbsolutePath();
    Assumptions.assumeTrue(Files.isDirectory(nx.resolve(".agents")));
    Assumptions.assumeTrue(Files.isExecutable(bin(nx)));
    return nx;
  }

  private static Path bin(Path nx) {
    return nx.resolve("arkannie").resolve("bin").resolve("arkannie");
  }

  private static void renderAgents(Path nx) throws AnnException {
    AgentRenderer.render(nx, new AgentsParams("haiku", 90, "haiku", 120, MediatorClimate.NONE));
  }

  // U5-05
  @Test
  void given_nexussyncHome_when_validate_then_rcZeroAndBothAgentsListed() throws Exception {
    Path nx = nexussyncDir();
    renderAgents(nx);

    LaunchResult r =
        new DefaultLauncher()
            .launch(
                List.of(bin(nx).toString(), "validate"),
                nx,
                Map.of("ARKANNIE_HOME", nx.toString()),
                Duration.ofMinutes(1));

    assertThat(r.timedOut()).isFalse();
    assertThat(r.exitCode()).isZero();
    // `validate` solo informa el recuento (medido en T-41); los nombres salen en `--catalog`
    // (D-23).
    assertThat(r.stdout()).contains("OK: 2 agent(s) valid");
    LaunchResult catalog =
        new DefaultLauncher()
            .launch(
                List.of(bin(nx).toString(), "--catalog"),
                nx,
                Map.of("ARKANNIE_HOME", nx.toString()),
                Duration.ofMinutes(1));
    assertThat(catalog.exitCode()).isZero();
    assertThat(catalog.stdout()).contains("[persona]").contains("[mediador]");
  }

  // U5-06
  @Test
  void given_sampleOfFifteen_when_personaPicksForReal_then_fiveIdsFromSampleWithReasons()
      throws Exception {
    Path nx = nexussyncDir();
    renderAgents(nx);
    Path runDir = Files.createDirectories(nx.resolve("runs").resolve("arkannie-agents-test"));
    final List<String> sampleIds = writeSample(nx, runDir.resolve("sample.json"));
    writeProfile(nx, runDir.resolve("profile_a.json"));
    Files.deleteIfExists(nx.resolve(".output").resolve(RUN_ID + ".md"));
    Path program = runDir.resolve("pick.ann");
    Files.writeString(program, pickProgram(runDir), StandardCharsets.UTF_8);
    RuntimeParams rt =
        new RuntimeParams(ExecutorKind.ARKANNIE, nx, bin(nx), Optional.empty(), 10, "0.3.0");

    RunOutput out =
        new ArkannieRunner(new DefaultLauncher()).run(program, RUN_ID, rt, Duration.ofMinutes(3));

    Envelope a = out.envelopes().get("a");
    assertThat(a).isNotNull();
    assertThat(a.status()).isEqualTo("success");
    assertThat(a.payload().get("picks")).asList().hasSize(K).doesNotHaveDuplicates();
    assertThat(a.payload().get("picks")).asList().isSubsetOf(sampleIds);
    assertThat(a.payload().get("reasons")).asList().hasSize(K);
  }

  private static String pickProgram(Path runDir) {
    return "# ann v0.3\n"
        + "// T-41: pick real de la persona sobre una muestra de 15, k=5.\n"
        + "parallel {\n"
        + "  [persona] --pick --id=a --profile=\""
        + runDir.resolve("profile_a.json")
        + "\" --sample=\""
        + runDir.resolve("sample.json")
        + "\" --k="
        + K
        + "\n"
        + "}\n"
        + "  each -> {\n"
        + "    [return] --id=r $result\n"
        + "  }\n";
  }

  /** First 15 activities of the synthetic catalog, by id, with what the persona needs to choose. */
  private static List<String> writeSample(Path nx, Path out) throws Exception {
    Map<String, Activity> all =
        new TreeMap<>(
            CatalogLoader.loadActivities(
                nx.resolve("fixtures").resolve("catalog-synthetic").resolve("activities.csv")));
    List<Map<String, Object>> items = new ArrayList<>();
    for (Activity act : all.values().stream().limit(SAMPLE_SIZE).toList()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("activity_id", act.activityId());
      m.put("title", act.title());
      m.put("activity_type", act.activityType());
      m.put("interests", act.interests());
      m.put("cost_mxn_pp", act.costMxnPp());
      m.put("duration_avg", act.durationAvg());
      m.put("ambience", act.ambience());
      items.add(m);
    }
    JSON.writeValue(out.toFile(), Map.of("items", items));
    return items.stream().map(m -> (String) m.get("activity_id")).toList();
  }

  /** Person A of the {@code opuestos} couple, without the truth weights of the bench. */
  private static void writeProfile(Path nx, Path out) throws IOException {
    JsonNode couple =
        JSON.readTree(nx.resolve("fixtures").resolve("couples").resolve("opuestos.json").toFile());
    ObjectNode a = (ObjectNode) couple.get("a").deepCopy();
    a.remove(List.of("truth_weights", "truthWeights", "location"));
    JSON.writeValue(out.toFile(), a);
  }
}
