package com.nexus.nexussync.ann;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.params.ExecutorKind;
import com.nexus.nexussync.params.RuntimeParams;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round trip of the real round templates through the real arkannie binary, with the {@code [echo]}
 * fixture agent of {@code nexussync/.spike/} standing in for {@code [persona]} and {@code
 * [mediador]} (T-37). Runs agents: tagged {@code arkannie}, excluded from the default build.
 */
@Tag("arkannie")
class ArkannieSpikeTest {

  private static final Duration TIMEOUT = Duration.ofMinutes(3);

  @TempDir Path home;

  private static Path nexussyncDir() {
    String dir = System.getProperty("nexussync.dir", "");
    Path nx = Path.of(dir.isEmpty() ? "no-such-dir" : dir).toAbsolutePath();
    Assumptions.assumeTrue(Files.isDirectory(nx.resolve(".spike").resolve(".agents")));
    Assumptions.assumeTrue(Files.isExecutable(bin(nx)));
    return nx;
  }

  private static Path bin(Path nx) {
    return nx.resolve("arkannie").resolve("bin").resolve("arkannie");
  }

  /** Copies the bare spike home: {@code .agents/} and {@code arkannie.config.yaml}. */
  private void copySpikeHome(Path nx) throws IOException {
    Path spike = nx.resolve(".spike");
    try (Stream<Path> files = Files.walk(spike.resolve(".agents"))) {
      for (Path src : files.toList()) {
        Path dst = home.resolve(spike.relativize(src).toString());
        if (Files.isDirectory(src)) {
          Files.createDirectories(dst);
        } else {
          Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
    Files.copy(spike.resolve("arkannie.config.yaml"), home.resolve("arkannie.config.yaml"));
  }

  /** The real template with every agent operation replaced by the {@code [echo]} fixture. */
  private Path echoTemplate(Path nx, String name) throws IOException {
    String real = Files.readString(nx.resolve("ann").resolve(name), StandardCharsets.UTF_8);
    String echo =
        real.replace("[persona] --pick ", "[echo] ")
            .replace("[persona] --vote ", "[echo] ")
            .replace("[mediador] --recommend ", "[echo] ")
            // [echo] no declara las banderas de datos de persona/mediador (D-23): se retiran.
            .replaceAll(" --(profile|sample|view|rubric|shortlist)=\"[^\"]*\"", "")
            .replaceAll(" --k=\\S+", "");
    Path out = home.resolve("ann").resolve(name);
    Files.createDirectories(out.getParent());
    return Files.writeString(out, echo, StandardCharsets.UTF_8);
  }

  private RunOutput run(Path nx, String template, Map<String, String> values, String runId)
      throws IOException, AnnException {
    Path program =
        ProgramRenderer.render(echoTemplate(nx, template), values, home.resolve(runId + ".ann"));
    RuntimeParams rt =
        new RuntimeParams(ExecutorKind.ARKANNIE, home, bin(nx), Optional.empty(), 10, "0.3.0");
    return new ArkannieRunner(new DefaultLauncher()).run(program, runId, rt, TIMEOUT);
  }

  // U6-12
  @Test
  void given_echoAgents_when_round1RunsForReal_then_outputReaderReturnsThreeEnvelopes()
      throws IOException, AnnException {
    Path nx = nexussyncDir();
    copySpikeHome(nx);
    Map<String, String> values =
        Map.of(
            "profile_a", home.resolve("profile_a.json").toString(),
            "profile_b", home.resolve("profile_b.json").toString(),
            "sample", home.resolve("sample.json").toString(),
            "view", home.resolve("mediator_view.json").toString(),
            "rubric", home.resolve("rubric.md").toString(),
            "k_pick", "15");

    RunOutput out = run(nx, "round1.ann.tmpl", values, "spike-rt-r1");

    assertThat(out.envelopes()).containsOnlyKeys("a", "b", "m");
    assertThat(out.envelopes().values()).extracting(Envelope::status).containsOnly("success");
    RunOutput reread = OutputReader.read(home, "spike-rt-r1");
    assertThat(reread.envelopes().keySet())
        .containsExactlyInAnyOrderElementsOf(List.of("a", "b", "m"));
  }

  // U6-12
  @Test
  void given_echoAgents_when_round2RunsForReal_then_outputReaderReturnsTwoEnvelopes()
      throws IOException, AnnException {
    Path nx = nexussyncDir();
    copySpikeHome(nx);
    Map<String, String> values =
        Map.of(
            "profile_a", home.resolve("profile_a.json").toString(),
            "profile_b", home.resolve("profile_b.json").toString(),
            "shortlist", home.resolve("shortlist.json").toString(),
            "k_vote", "5");

    RunOutput out = run(nx, "round2.ann.tmpl", values, "spike-rt-r2");

    assertThat(out.envelopes()).containsOnlyKeys("a", "b");
    assertThat(out.envelopes().values()).extracting(Envelope::status).containsOnly("success");
  }
}
