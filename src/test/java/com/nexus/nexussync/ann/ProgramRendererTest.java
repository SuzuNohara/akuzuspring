package com.nexus.nexussync.ann;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link ProgramRenderer}: {@code {{key}}} substitution by path, escaping and brace rejection. */
class ProgramRendererTest {

  private static final String RESOURCE = "/nexussync/ann/test-min.ann.tmpl";

  private static Path minimalTemplate() throws URISyntaxException {
    return Path.of(
        Objects.requireNonNull(ProgramRendererTest.class.getResource(RESOURCE), RESOURCE).toURI());
  }

  // U6-01
  @Test
  void given_templateByPath_when_render_then_everyPlaceholderReplaced(@TempDir Path tmp)
      throws Exception {
    Path out = tmp.resolve("runs").resolve("r1").resolve("round1.ann");
    Map<String, String> values = Map.of("k", "5", "sample", "sample.json", "note", "plain");

    Path rendered = ProgramRenderer.render(minimalTemplate(), values, out);

    assertThat(rendered).isEqualTo(out);
    String text = Files.readString(out, UTF_8);
    assertThat(text).startsWith("# ann v0.3\n");
    assertThat(text).contains("$k = 5\n").contains("$sample = \"sample.json\"\n");
    assertThat(text).contains("$note = \"plain\"\n").contains("[return] --id=r $k\n");
    assertThat(text).doesNotContain("{{").doesNotContain("}}");
  }

  // U6-01
  @Test
  void given_valueWithQuotes_when_render_then_quotesEscaped(@TempDir Path tmp) throws Exception {
    Path out = tmp.resolve("out.ann");
    Map<String, String> values = Map.of("k", "1", "sample", "s.json", "note", "say \"hi\"");

    ProgramRenderer.render(minimalTemplate(), values, out);

    assertThat(Files.readString(out, UTF_8)).contains("$note = \"say \\\"hi\\\"\"\n");
  }

  // U6-01
  @Test
  void given_extraValuesAndRepeatedKey_when_render_then_allOccurrencesReplaced(@TempDir Path tmp)
      throws Exception {
    Path template = tmp.resolve("t.tmpl");
    Files.writeString(template, "a={{x}} b={{ x }} c={{y}}\n", UTF_8);
    Path out = tmp.resolve("t.ann");

    ProgramRenderer.render(template, Map.of("x", "1", "y", "2", "unused", "z"), out);

    assertThat(out).hasContent("a=1 b=1 c=2\n");
  }

  // U6-02
  @Test
  void given_placeholderWithoutValue_when_render_then_renderErrorNamesKey(@TempDir Path tmp)
      throws Exception {
    Path out = tmp.resolve("out.ann");
    Map<String, String> values = Map.of("k", "1", "sample", "s.json");

    AnnException ex =
        catchThrowableOfType(
            () -> ProgramRenderer.render(minimalTemplate(), values, out), AnnException.class);

    assertThat(ex.kind()).isEqualTo(AnnException.Kind.RENDER);
    assertThat(ex.getMessage()).contains("note");
    assertThat(out).doesNotExist();
  }

  // U6-02
  @Test
  void given_valueWithOpeningBraces_when_render_then_renderError(@TempDir Path tmp)
      throws Exception {
    Path out = tmp.resolve("out.ann");
    Map<String, String> values = Map.of("k", "1", "sample", "{{k}}", "note", "n");

    AnnException ex =
        catchThrowableOfType(
            () -> ProgramRenderer.render(minimalTemplate(), values, out), AnnException.class);

    assertThat(ex.kind()).isEqualTo(AnnException.Kind.RENDER);
    assertThat(ex.getMessage()).contains("sample");
    assertThat(out).doesNotExist();
  }

  // U6-02
  @Test
  void given_valueWithClosingBraces_when_render_then_renderError(@TempDir Path tmp)
      throws Exception {
    Path out = tmp.resolve("out.ann");
    Map<String, String> values = Map.of("k", "1", "sample", "s", "note", "a }} b");

    AnnException ex =
        catchThrowableOfType(
            () -> ProgramRenderer.render(minimalTemplate(), values, out), AnnException.class);

    assertThat(ex.kind()).isEqualTo(AnnException.Kind.RENDER);
    assertThat(ex.getMessage()).contains("note");
  }

  // U6-02
  @Test
  void given_malformedPlaceholder_when_render_then_renderError(@TempDir Path tmp)
      throws IOException {
    Path template = tmp.resolve("bad.tmpl");
    Files.writeString(template, "x={{x}} y={{ y z }}\n", UTF_8);
    Path out = tmp.resolve("bad.ann");

    AnnException ex =
        catchThrowableOfType(
            () -> ProgramRenderer.render(template, Map.of("x", "1"), out), AnnException.class);

    assertThat(ex.kind()).isEqualTo(AnnException.Kind.RENDER);
    assertThat(out).doesNotExist();
  }

  // U6-02
  @Test
  void given_missingTemplate_when_render_then_notFound(@TempDir Path tmp) {
    Path template = tmp.resolve("absent.tmpl");
    Path out = tmp.resolve("out.ann");

    AnnException ex =
        catchThrowableOfType(
            () -> ProgramRenderer.render(template, Map.of(), out), AnnException.class);

    assertThat(ex.kind()).isEqualTo(AnnException.Kind.NOT_FOUND);
    assertThat(ex.getMessage()).contains("absent.tmpl");
    assertThat(ex).hasCauseInstanceOf(IOException.class);
  }

  // U6-02
  @Test
  void given_unwritableOutput_when_render_then_renderErrorWithCause(@TempDir Path tmp)
      throws IOException {
    Path template = tmp.resolve("t.tmpl");
    Files.writeString(template, "v={{v}}\n", UTF_8);
    Path blocker = tmp.resolve("file-not-dir");
    Files.writeString(blocker, "", UTF_8);
    Path out = blocker.resolve("out.ann");

    AnnException ex =
        catchThrowableOfType(
            () -> ProgramRenderer.render(template, Map.of("v", "1"), out), AnnException.class);

    assertThat(ex.kind()).isEqualTo(AnnException.Kind.RENDER);
    assertThat(ex).hasCauseInstanceOf(IOException.class);
  }
}
