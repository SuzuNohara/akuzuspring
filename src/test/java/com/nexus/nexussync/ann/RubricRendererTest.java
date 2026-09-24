package com.nexus.nexussync.ann;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.params.RubricParams;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RubricRendererTest {

  @TempDir Path tmp;

  private static RubricParams rubric() {
    return new RubricParams(
        Map.of("novedad", 0.1, "compatibilidad", 0.35, "equilibrio", 0.55),
        Map.of("max_por_tipo", 2, "min_alternativas_hogar", 1),
        Map.of("repetida_reciente", 0.5, "presupuesto_excedido", 1.0));
  }

  // U5-04
  @Test
  void givenRubricParams_whenRender_thenHeaderAndSortedTablesWithValues() throws Exception {
    Path out = tmp.resolve("run/rubric.md");

    Path written = RubricRenderer.render(rubric(), out);

    String text = Files.readString(written, StandardCharsets.UTF_8);
    assertThat(written).isEqualTo(out);
    assertThat(text).startsWith(RubricRenderer.HEADER);
    assertThat(text)
        .containsSubsequence(
            "## Criterios",
            "| `compatibilidad` | 0.35 |",
            "| `equilibrio` | 0.55 |",
            "| `novedad` | 0.1 |",
            "## Balance",
            "| `max_por_tipo` | 2 |",
            "| `min_alternativas_hogar` | 1 |",
            "## Descarte",
            "| `presupuesto_excedido` | 1.0 |",
            "| `repetida_reciente` | 0.5 |");
    assertThat(text).doesNotContain(RubricRenderer.NORMATIVE);
  }

  // U5-04
  @Test
  void givenSpecInNexussyncDir_whenRenderWithDir_thenNormativeTextAppendedAfterTables()
      throws Exception {
    Path specs = Files.createDirectories(tmp.resolve("nx/specs"));
    Files.writeString(specs.resolve("rubric-mediador.md"), "# Spec\ntexto normativo\n");

    Path out = RubricRenderer.render(rubric(), tmp.resolve("nx"), tmp.resolve("rubric.md"));

    String text = Files.readString(out, StandardCharsets.UTF_8);
    assertThat(text)
        .containsSubsequence("## Descarte", RubricRenderer.NORMATIVE, "# Spec\ntexto normativo\n");
    assertThat(text).endsWith("texto normativo\n");
  }

  // U5-04
  @Test
  void givenRealSpec_whenRenderWithDir_thenContainsRealNormativeSections() throws Exception {
    String dir = System.getProperty("nexussync.dir", "");
    Path real = Path.of(dir.isEmpty() ? "nexussync-dir-not-set" : dir);
    Assumptions.assumeTrue(Files.isDirectory(real.resolve("specs")), "nexussync.dir not set");

    Path out = RubricRenderer.render(rubric(), real, tmp.resolve("rubric.md"));

    assertThat(Files.readString(out, StandardCharsets.UTF_8))
        .containsSubsequence("| `compatibilidad` | 0.35 |", "## 4. Reglas de descarte");
  }

  @Test
  void givenMissingSpec_whenRenderWithDir_thenNotFound() {
    assertThatThrownBy(() -> RubricRenderer.render(rubric(), tmp, tmp.resolve("rubric.md")))
        .isInstanceOfSatisfying(
            AnnException.class, e -> assertThat(e.kind()).isEqualTo(AnnException.Kind.NOT_FOUND));
    assertThat(tmp.resolve("rubric.md")).doesNotExist();
  }

  @Test
  void givenKeyWithPipe_whenRender_thenRenderError() {
    RubricParams bad = new RubricParams(Map.of("a|b", 1.0), Map.of(), Map.of());

    assertRenderError(bad, tmp.resolve("rubric.md"));
  }

  @Test
  void givenNonFiniteWeight_whenRender_thenRenderError() {
    RubricParams bad = new RubricParams(Map.of("a", Double.NaN), Map.of(), Map.of());

    assertRenderError(bad, tmp.resolve("rubric.md"));
  }

  @Test
  void givenOutputIsDirectory_whenRender_thenRenderError() throws Exception {
    Path out = Files.createDirectories(tmp.resolve("taken"));

    assertRenderError(rubric(), out);
  }

  private static void assertRenderError(RubricParams r, Path out) {
    assertThatThrownBy(() -> RubricRenderer.render(r, out))
        .isInstanceOfSatisfying(
            AnnException.class, e -> assertThat(e.kind()).isEqualTo(AnnException.Kind.RENDER));
  }
}
