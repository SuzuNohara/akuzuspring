package com.nexus.nexussync.ann;

import com.nexus.nexussync.params.RubricParams;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Renders the per-run {@code rubric.md} that the mediator agent receives in {@code context.rubric}
 * (unit U5).
 *
 * <p>The file has a fixed header, a criteria table with the weights of {@link
 * RubricParams#criteria()}, a balance table and a discard table, each sorted alphabetically by key
 * so the output is deterministic. The overload that receives the nexussync directory appends the
 * normative text of {@code specs/rubric-mediador.md} after the tables.
 */
public final class RubricRenderer {

  /** Fixed header of every rendered rubric. */
  static final String HEADER =
      "# Rúbrica del Mediador — valores de esta corrida\n\n"
          + "> Generada por RubricRenderer desde los RubricParams del experimento. Los valores de"
          + " estas tablas\n"
          + "> son los de esta corrida y prevalecen sobre los parámetros v0.1 que cite el texto"
          + " normativo.\n";

  /** Separator written before the normative text of the spec. */
  static final String NORMATIVE = "\n---\n\n## Texto normativo (specs/rubric-mediador.md)\n\n";

  /** Keys become table cells: word characters, dots and dashes only, so no {@code |} or newline. */
  private static final Pattern KEY = Pattern.compile("[\\w.-]+");

  private RubricRenderer() {}

  /**
   * Writes the rubric tables (header, criteria, balance, discard) to {@code out}.
   *
   * @param r rubric parameters of the run
   * @param out path of the rendered rubric, overwritten if present; parents are created
   * @return {@code out}
   * @throws AnnException {@code RENDER} if a key is malformed, a value is not finite or the file
   *     cannot be written
   * @implNote O(n log n) time and O(n) space, n = total number of rubric entries.
   */
  public static Path render(RubricParams r, Path out) throws AnnException {
    Objects.requireNonNull(out, "out");
    write(out, tables(r));
    return out;
  }

  /**
   * Writes the rubric tables followed by the normative text of {@code
   * nexussyncDir/specs/rubric-mediador.md} to {@code out}.
   *
   * @param r rubric parameters of the run
   * @param nexussyncDir root of the nexussync directory
   * @param out path of the rendered rubric, overwritten if present; parents are created
   * @return {@code out}
   * @throws AnnException {@code NOT_FOUND} if the spec cannot be read; {@code RENDER} if a key is
   *     malformed, a value is not finite or the file cannot be written
   * @implNote O(n log n + s) time and O(n + s) space, n = rubric entries, s = spec length.
   */
  public static Path render(RubricParams r, Path nexussyncDir, Path out) throws AnnException {
    Objects.requireNonNull(out, "out");
    Path spec = nexussyncDir.resolve("specs").resolve("rubric-mediador.md");
    String tables = tables(r);
    write(out, tables + NORMATIVE + read(spec));
    return out;
  }

  private static String tables(RubricParams r) throws AnnException {
    Objects.requireNonNull(r, "r");
    StringBuilder sb = new StringBuilder(HEADER);
    section(sb, "## Criterios (pesos, suman 1)", "Criterio", "Peso", r.criteria());
    section(sb, "## Balance", "Parámetro", "Valor", r.balance());
    section(sb, "## Descarte", "Regla", "Penalización", r.descarte());
    return sb.toString();
  }

  private static void section(
      StringBuilder sb,
      String title,
      String keyColumn,
      String valueColumn,
      Map<String, ? extends Number> values)
      throws AnnException {
    sb.append('\n').append(title).append("\n\n");
    sb.append("| ").append(keyColumn).append(" | ").append(valueColumn).append(" |\n|---|---|\n");
    for (Map.Entry<String, ? extends Number> e : new TreeMap<>(values).entrySet()) {
      String key = e.getKey();
      if (!KEY.matcher(key).matches()) {
        throw new AnnException(AnnException.Kind.RENDER, "malformed rubric key: " + key);
      }
      sb.append("| `").append(key).append("` | ").append(number(key, e.getValue())).append(" |\n");
    }
  }

  private static String number(String key, Number value) throws AnnException {
    if (value instanceof Double d) {
      if (!Double.isFinite(d)) {
        throw new AnnException(AnnException.Kind.RENDER, "rubric value of " + key + " not finite");
      }
      return BigDecimal.valueOf(d).toPlainString();
    }
    return value.toString();
  }

  private static String read(Path spec) throws AnnException {
    try {
      return Files.readString(spec, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AnnException(AnnException.Kind.NOT_FOUND, "cannot read rubric spec " + spec, e);
    }
  }

  private static void write(Path out, String content) throws AnnException {
    try {
      Path parent = out.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(out, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AnnException(AnnException.Kind.RENDER, "cannot write rubric " + out, e);
    }
  }
}
