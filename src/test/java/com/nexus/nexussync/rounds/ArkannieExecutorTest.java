package com.nexus.nexussync.rounds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.ann.ArkannieRunner;
import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.ann.FakeLauncher;
import com.nexus.nexussync.ann.LaunchResult;
import com.nexus.nexussync.params.Params;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArkannieExecutorTest {

  private static final String RUN = "exp-c1-1758672000000-42";
  private static final String ROUND1_TMPL =
      "# ann v0.3\n"
          + "// test template\n"
          + "parallel {\n"
          + "  [persona] --pick --id=a --profile=\"{{profile_a}}\" --sample=\"{{sample}}\""
          + " --k={{k_pick}}\n"
          + "  [persona] --pick --id=b --profile=\"{{profile_b}}\" --sample=\"{{sample}}\""
          + " --k={{k_pick}}\n"
          + "  [mediador] --recommend --id=m --view=\"{{view}}\" --sample=\"{{sample}}\""
          + " --rubric=\"{{rubric}}\" --k={{k_pick}}\n"
          + "}\n"
          + "  each -> {\n"
          + "    [return] --id=r $result\n"
          + "  }\n";
  private static final String ROUND2_TMPL =
      "# ann v0.3\n"
          + "parallel {\n"
          + "  [persona] --vote --id=a --profile=\"{{profile_a}}\" --shortlist=\"{{shortlist}}\""
          + " --k={{k_vote}}\n"
          + "  [persona] --vote --id=b --profile=\"{{profile_b}}\" --shortlist=\"{{shortlist}}\""
          + " --k={{k_vote}}\n"
          + "}\n"
          + "  each -> {\n"
          + "    [return] --id=r $result\n"
          + "  }\n";

  @TempDir Path home;

  private Path runDir;
  private Params params;

  @BeforeEach
  void setUp() throws IOException {
    Path ann = Files.createDirectories(home.resolve(ArkannieExecutor.ANN_DIR));
    Files.writeString(ann.resolve("round1.ann.tmpl"), ROUND1_TMPL, StandardCharsets.UTF_8);
    Files.writeString(ann.resolve("round2.ann.tmpl"), ROUND2_TMPL, StandardCharsets.UTF_8);
    runDir = home.resolve("runs").resolve(RUN);
    params = GateTest.params(GateTest.defaultRounds(), Path.of("elsewhere"));
  }

  private static Path fixture(String name) throws URISyntaxException {
    return Path.of(
        Objects.requireNonNull(
                ArkannieExecutorTest.class.getResource("/nexussync/output/" + name), name)
            .toURI());
  }

  private FakeLauncher launcherCopying(String fixture, String runId) throws URISyntaxException {
    return new FakeLauncher()
        .copyOutput(fixture(fixture), home.resolve(".output").resolve(runId + ".md"));
  }

  private ArkannieExecutor executor(FakeLauncher launcher) {
    return new ArkannieExecutor(new ArkannieRunner(launcher), home);
  }

  private Map<Agent, Optional<Envelope>> round1(ArkannieExecutor ex) throws AnnException {
    return ex.round1(runDir, GateTest.context(), GateTest.sample(), params);
  }

  // U7-16
  @Test
  void given_launcherWritesOk_when_round1_then_threeEnvelopesAndTheFiveTraces() throws Exception {
    FakeLauncher launcher = launcherCopying("ok.md", RUN + "-r1");

    Map<Agent, Optional<Envelope>> env = round1(executor(launcher));

    assertThat(env).containsOnlyKeys(Agent.A, Agent.B, Agent.M);
    assertThat(env.get(Agent.A)).get().extracting(Envelope::payload).isEqualTo(picks("x", "y"));
    assertThat(env.get(Agent.B)).get().extracting(Envelope::payload).isEqualTo(picks("y", "z"));
    assertThat(env.get(Agent.M)).get().extracting(Envelope::status).isEqualTo("success");
    GateTest.assertTraces(runDir);
    assertThat(runDir.resolve("round1.out.md"))
        .hasSameTextualContentAs(fixture("ok.md"), StandardCharsets.UTF_8);
    Path program = runDir.resolve("round1.ann");
    assertThat(launcher.lastCmd())
        .containsExactly(
            Path.of("elsewhere", "arkannie", "bin", "arkannie").toString(),
            "--id=" + RUN + "-r1",
            program.toAbsolutePath().toString());
    assertThat(launcher.lastEnv()).containsEntry("ARKANNIE_HOME", home.toAbsolutePath().toString());
    assertThat(launcher.lastTimeout()).contains(Duration.ofSeconds(150));
    assertThat(Files.readString(program, StandardCharsets.UTF_8))
        .contains("profile=\"" + runDir.toAbsolutePath().resolve(Gate.PROFILE_A) + "\"")
        .contains("view=\"" + runDir.toAbsolutePath().resolve(Gate.VIEW) + "\"")
        .contains("--k=5")
        .contains("--id=m")
        .doesNotContain("{{");
  }

  private static Map<String, Object> picks(String... ids) {
    List<String> reasons =
        ids[0].equals("x")
            ? List.of("cerca de casa", "novedad")
            : List.of("le gusta el aire libre", "entra en el presupuesto");
    return Map.of("picks", List.of(ids), "reasons", reasons);
  }

  // U7-16
  @Test
  void given_outputWithoutMediator_when_round1_then_mediatorEmpty() throws Exception {
    Map<Agent, Optional<Envelope>> env = round1(executor(launcherCopying("no-m.md", RUN + "-r1")));

    assertThat(env.get(Agent.M)).isEmpty();
    assertThat(env.get(Agent.A)).isPresent();
  }

  // U7-16
  @Test
  void given_shortlist_when_round2_then_votesOfBothPersonasAndShortlistWritten() throws Exception {
    FakeLauncher launcher = launcherCopying("ok.md", RUN + "-r2");
    ArkannieExecutor ex = executor(launcher);

    Map<Agent, Optional<Envelope>> env = ex.round2(runDir, List.of("x", "y"), params);

    assertThat(env).containsOnlyKeys(Agent.A, Agent.B);
    assertThat(env.get(Agent.A)).isPresent();
    assertThat(env.get(Agent.B)).isPresent();
    assertThat(launcher.lastCmd()).contains("--id=" + RUN + "-r2");
    assertThat(runDir.resolve("round2.out.md")).isRegularFile();
    Map<?, ?> shortlist =
        new ObjectMapper()
            .readValue(runDir.resolve(ArkannieExecutor.SHORTLIST).toFile(), Map.class);
    assertThat(shortlist.get("shortlist")).isEqualTo(List.of("x", "y"));
    assertThat(Files.readString(runDir.resolve("round2.ann"), StandardCharsets.UTF_8))
        .contains("--k=2")
        .contains("shortlist=\"" + runDir.toAbsolutePath().resolve(ArkannieExecutor.SHORTLIST));
  }

  // U7-16
  @Test
  void given_arkannieExitsWithError_when_round1_then_allAgentsEmpty()
      throws AnnException, IOException {
    FakeLauncher launcher =
        new FakeLauncher().onLaunch(cmd -> LaunchResult.of(1, "", "boom", false, Duration.ZERO));

    Map<Agent, Optional<Envelope>> env = round1(executor(launcher));

    assertThat(env).containsOnlyKeys(Agent.A, Agent.B, Agent.M);
    assertThat(env.values()).allSatisfy(e -> assertThat(e).isEmpty());
    assertThat(runDir.resolve("round1.out.md")).doesNotExist();
    GateTest.assertTraces(runDir);
  }

  // U7-16
  @Test
  void given_arkannieTimesOut_when_round2_then_bothPersonasEmpty() throws AnnException {
    FakeLauncher launcher =
        new FakeLauncher().onLaunch(cmd -> LaunchResult.of(0, "", "", true, Duration.ZERO));

    Map<Agent, Optional<Envelope>> env = executor(launcher).round2(runDir, List.of("x"), params);

    assertThat(env).containsOnlyKeys(Agent.A, Agent.B);
    assertThat(env.values()).allSatisfy(e -> assertThat(e).isEmpty());
  }

  // U7-16
  @Test
  void given_roundRepeatedInSameRunDir_when_round1_then_eachAttemptHasItsOwnRunId()
      throws Exception {
    FakeLauncher launcher = launcherCopying("ok.md", RUN + "-r1");
    ArkannieExecutor ex = executor(launcher);

    round1(ex);
    Map<Agent, Optional<Envelope>> retry = round1(ex);
    assertThat(launcher.lastCmd()).contains("--id=" + RUN + "-r1-2");
    round1(ex);

    assertThat(retry.values()).allSatisfy(e -> assertThat(e).isEmpty());
    assertThat(launcher.lastCmd()).contains("--id=" + RUN + "-r1-3");
    assertThat(launcher.launches()).isEqualTo(3);
    assertThat(runDir.resolve("round1-2.ann")).isRegularFile();
    assertThat(runDir.resolve("round1-3.ann")).isRegularFile();
  }

  // D-24
  @Test
  void given_retryOfOnePersona_when_round1_then_programOnlyDispatchesThatPersona()
      throws Exception {
    FakeLauncher launcher = launcherCopying("ok.md", RUN + "-r1");
    ArkannieExecutor ex = executor(launcher);
    round1(ex);

    Map<Agent, Optional<Envelope>> retry =
        ex.round1(runDir, GateTest.context(), GateTest.sample(), params, EnumSet.of(Agent.B));

    assertThat(retry).containsOnlyKeys(Agent.B);
    String program = Files.readString(runDir.resolve("round1-2.ann"), StandardCharsets.UTF_8);
    assertThat(program)
        .contains("[persona] --pick --id=b")
        .contains("[return] --id=r $result")
        .doesNotContain("--id=a")
        .doesNotContain("--id=m");
    assertThat(Files.readString(runDir.resolve("round1.ann"), StandardCharsets.UTF_8))
        .contains("--id=a", "--id=b", "--id=m");
  }

  // D-22
  @Test
  void given_tracesAlreadyWritten_when_round1_then_sampleNotOverwritten() throws Exception {
    Files.createDirectories(runDir);
    Files.writeString(runDir.resolve(Gate.SAMPLE), "{\"items\":[]}", StandardCharsets.UTF_8);

    round1(executor(new FakeLauncher()));

    assertThat(runDir.resolve(Gate.SAMPLE)).hasContent("{\"items\":[]}");
  }

  // U7-16
  @Test
  void given_lockedOrAgentsError_when_classified_then_propagatedOthersDegrade() {
    assertThat(ArkannieExecutor.degrades(new AnnException(AnnException.Kind.LOCKED, "l")))
        .isFalse();
    assertThat(ArkannieExecutor.degrades(new AnnException(AnnException.Kind.AGENTS, "g")))
        .isFalse();
    assertThat(ArkannieExecutor.degrades(new AnnException(AnnException.Kind.EXIT, "e"))).isTrue();
    assertThat(ArkannieExecutor.degrades(new AnnException(AnnException.Kind.TIMEOUT, "t")))
        .isTrue();
  }

  // U7-16
  @Test
  void given_missingTemplate_when_round1_then_renderErrorPropagates() throws IOException {
    Files.delete(home.resolve(ArkannieExecutor.ANN_DIR).resolve("round1.ann.tmpl"));
    FakeLauncher launcher = new FakeLauncher();

    assertThatThrownBy(() -> round1(executor(launcher)))
        .isInstanceOfSatisfying(
            AnnException.class, e -> assertThat(e.kind()).isEqualTo(AnnException.Kind.NOT_FOUND));
    assertThat(launcher.launches()).isZero();
  }

  // U7-16
  @Test
  void given_runDirIsRegularFile_when_round2_then_renderError() throws IOException {
    Path file = Files.writeString(home.resolve("plain"), "x", StandardCharsets.UTF_8);
    ArkannieExecutor ex = executor(new FakeLauncher());

    assertThatThrownBy(() -> ex.round2(file, List.of("x"), params))
        .isInstanceOfSatisfying(
            AnnException.class, e -> assertThat(e.kind()).isEqualTo(AnnException.Kind.RENDER));
  }

  // U6-12 (render side): the real templates of nexussync/ann render with no placeholder left.
  @Test
  void given_realTemplates_when_bothRounds_then_programsCarryAbsolutePathsAndOperations()
      throws Exception {
    String dir = System.getProperty("nexussync.dir", "");
    Path real = Path.of(dir.isEmpty() ? "no-such-dir" : dir).resolve(ArkannieExecutor.ANN_DIR);
    Assumptions.assumeTrue(Files.isRegularFile(real.resolve("round1.ann.tmpl")));
    for (String name : List.of("round1.ann.tmpl", "round2.ann.tmpl")) {
      Files.copy(
          real.resolve(name),
          home.resolve(ArkannieExecutor.ANN_DIR).resolve(name),
          StandardCopyOption.REPLACE_EXISTING);
    }
    ArkannieExecutor ex = executor(new FakeLauncher());

    round1(ex);
    ex.round2(runDir, List.of("x", "y", "z"), params);

    String r1 = Files.readString(runDir.resolve("round1.ann"), StandardCharsets.UTF_8);
    String r2 = Files.readString(runDir.resolve("round2.ann"), StandardCharsets.UTF_8);
    assertThat(r1)
        .startsWith("# ann v0.3")
        .contains("[persona] --pick --id=a", "[persona] --pick --id=b")
        .contains("[mediador] --recommend --id=m", "[return] --id=r $result")
        .contains("rubric=\"" + runDir.toAbsolutePath().resolve(Gate.RUBRIC) + "\"")
        .doesNotContain("{{");
    assertThat(r2)
        .startsWith("# ann v0.3")
        .contains("[persona] --vote --id=a", "[persona] --vote --id=b", "--k=3")
        .doesNotContain("{{")
        .doesNotContain("[mediador]");
  }
}
