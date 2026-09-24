package com.nexus.nexussync.ann;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputReaderTest {

  private static final String RUN_ID = "base-c1-1758672000000-42";
  private static final String FRONT =
      "---\nid: "
          + RUN_ID
          + "\nagent: nexussync\nstatus: ok\nfinished: 2026-09-24T00:10:09Z\n---\n";

  @TempDir Path home;

  private void installFixture(String name) throws IOException, URISyntaxException {
    URL url =
        Objects.requireNonNull(
            OutputReaderTest.class.getResource("/nexussync/output/" + name), name);
    writeOutput(Files.readString(Path.of(url.toURI()), StandardCharsets.UTF_8));
  }

  private void writeOutput(String content) throws IOException {
    Path out = home.resolve(".output").resolve(RUN_ID + ".md");
    Files.createDirectories(out.getParent());
    Files.writeString(out, content, StandardCharsets.UTF_8);
  }

  private static String block(String yaml) {
    return "\n## r-1\n\n```yaml\n" + yaml + "```\n";
  }

  // U6-03
  @Test
  void givenOkOutput_whenRead_thenEnvelopesAreIndexedByBlockId() throws Exception {
    installFixture("ok.md");

    RunOutput out = OutputReader.read(home, RUN_ID);

    assertThat(out.runId()).isEqualTo(RUN_ID);
    assertThat(out.status()).isEqualTo("ok");
    assertThat(out.stdout()).isEmpty();
    assertThat(out.stderr()).isEmpty();
    assertThat(out.elapsed()).isEqualTo(Duration.ZERO);
    assertThat(out.envelopes()).containsOnlyKeys("a", "b", "m");
    Envelope a = out.envelopes().get("a");
    assertThat(a.status()).isEqualTo("ok");
    assertThat(a.payload()).containsEntry("picks", List.of("x", "y"));
    assertThat(a.payload()).containsEntry("reasons", List.of("cerca de casa", "novedad"));
    assertThat(out.envelopes().get("b").payload()).containsEntry("picks", List.of("y", "z"));
    assertThat(out.envelopes().get("m").payload()).containsEntry("picks", List.of("y", "x"));
  }

  // U6-04
  @Test
  void givenOutputWithoutMediator_whenRead_thenMediatorIsAbsentWithoutError() throws Exception {
    installFixture("no-m.md");

    RunOutput out = OutputReader.read(home, RUN_ID);

    assertThat(out.envelopes()).containsOnlyKeys("a", "b");
  }

  // U6-04
  @Test
  void givenFrontMatterWithoutClosingFence_whenRead_thenParseError() throws Exception {
    installFixture("corrupt-frontmatter.md");

    assertThatThrownBy(() -> OutputReader.read(home, RUN_ID))
        .isInstanceOfSatisfying(
            AnnException.class, e -> assertThat(e.kind()).isEqualTo(AnnException.Kind.PARSE))
        .hasMessageContaining("closing");
  }

  // U6-04
  @Test
  void givenNoOutputFile_whenRead_thenNotFound() {
    assertThatThrownBy(() -> OutputReader.read(home, RUN_ID))
        .isInstanceOfSatisfying(
            AnnException.class, e -> assertThat(e.kind()).isEqualTo(AnnException.Kind.NOT_FOUND));
  }

  @Test
  void givenFileWithoutFrontMatter_whenRead_thenParseError() throws Exception {
    writeOutput("## r-1\n");

    assertParseError();
  }

  @Test
  void givenEmptyFile_whenRead_thenParseError() throws Exception {
    writeOutput("");

    assertParseError();
  }

  @Test
  void givenInvalidFrontMatterYaml_whenRead_thenParseError() throws Exception {
    writeOutput("---\nstatus: [unclosed\n---\n");

    assertParseError();
  }

  @Test
  void givenFrontMatterWithoutStatus_whenRead_thenParseError() throws Exception {
    writeOutput("---\nid: x\n---\n");

    assertParseError();
  }

  @Test
  void givenEmptyFrontMatter_whenRead_thenParseError() throws Exception {
    writeOutput("---\n---\n");

    assertParseError();
  }

  @Test
  void givenBlockWithoutId_whenRead_thenParseError() throws Exception {
    writeOutput(FRONT + block("status: ok\npayload:\n  picks: [x]\n"));

    assertParseError();
  }

  @Test
  void givenInvalidBlockYaml_whenRead_thenParseError() throws Exception {
    writeOutput(FRONT + block("id: a\npayload: {picks: [x\n"));

    assertParseError();
  }

  @Test
  void givenRepeatedId_whenRead_thenParseError() throws Exception {
    writeOutput(FRONT + block("id: a\nstatus: ok\n") + block("id: a\nstatus: ok\n"));

    assertParseError();
  }

  @Test
  void givenUnterminatedBlock_whenRead_thenParseError() throws Exception {
    writeOutput(FRONT + "\n## r-1\n\n```yaml\nid: a\n");

    assertParseError();
  }

  @Test
  void givenBlockWithoutStatusOrPayload_whenRead_thenEmptyStatusAndPayload() throws Exception {
    writeOutput(FRONT + block("id: a\n"));

    Envelope a = OutputReader.read(home, RUN_ID).envelopes().get("a");

    assertThat(a.status()).isEmpty();
    assertThat(a.payload()).isEmpty();
  }

  @Test
  void givenScalarPayload_whenRead_thenWrappedUnderValue() throws Exception {
    writeOutput(FRONT + block("id: m\nstatus: ok\npayload: hola\n"));

    Envelope m = OutputReader.read(home, RUN_ID).envelopes().get("m");

    assertThat(m.payload()).containsExactly(Map.entry("value", "hola"));
  }

  @Test
  void givenNonStringPayloadKeys_whenRead_thenKeysAreStringified() throws Exception {
    writeOutput(FRONT + block("id: 7\nstatus: ok\npayload:\n  1: uno\n"));

    Envelope seven = OutputReader.read(home, RUN_ID).envelopes().get("7");

    assertThat(seven.payload()).containsEntry("1", "uno");
  }

  @Test
  void givenParsedOutput_whenMutatingEnvelopes_thenUnsupported() throws Exception {
    installFixture("ok.md");
    RunOutput out = OutputReader.read(home, RUN_ID);

    assertThatThrownBy(() -> out.envelopes().remove("a"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> out.envelopes().get("a").payload().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void givenRunOutput_whenWithLaunch_thenProcessDataIsReplaced() throws Exception {
    installFixture("ok.md");
    RunOutput out = OutputReader.read(home, RUN_ID);

    RunOutput done = out.withLaunch("so", "se", Duration.ofSeconds(3));

    assertThat(done.stdout()).isEqualTo("so");
    assertThat(done.stderr()).isEqualTo("se");
    assertThat(done.elapsed()).isEqualTo(Duration.ofSeconds(3));
    assertThat(done.envelopes()).isEqualTo(out.envelopes());
  }

  private void assertParseError() {
    assertThatThrownBy(() -> OutputReader.read(home, RUN_ID))
        .isInstanceOfSatisfying(
            AnnException.class, e -> assertThat(e.kind()).isEqualTo(AnnException.Kind.PARSE));
  }
}
