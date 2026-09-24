package com.nexus.nexussync.ann;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders an Ann program from a <code>{{key}}</code> template read by path (v2: from {@code
 * nexussyncDir/ann/}, never from the classpath).
 *
 * <p>Values are inserted verbatim, so the template decides where quotes go; the only rewriting is
 * that a {@code "} inside a value becomes {@code \"}. A value that contains <code>{{</code> or
 * <code>}}</code> is refused, so a rendered program can never grow new placeholders, and a
 * placeholder left without a value aborts the render before anything is written.
 */
public final class ProgramRenderer {

  /** <code>{{key}}</code> with optional inner spaces; keys are word characters, dots and dashes. */
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*\\}\\}");

  private static final String OPEN = "{{";
  private static final String CLOSE = "}}";

  private ProgramRenderer() {}

  /**
   * Substitutes every <code>{{key}}</code> of {@code template} with {@code values.get(key)} and
   * writes the result to {@code out}, creating parent directories as needed.
   *
   * @param template path of the template file (UTF-8)
   * @param values value per placeholder key; extra keys are ignored
   * @param out path of the rendered program, overwritten if present
   * @return {@code out}
   * @throws AnnException {@code NOT_FOUND} if the template cannot be read; {@code RENDER} if a
   *     placeholder has no value, a placeholder is malformed, a value contains <code>{{</code> or
   *     <code>}}</code>, or the output cannot be written
   * @implNote O(n + v) time and space, n = template length, v = total length of the values used.
   */
  public static Path render(Path template, Map<String, String> values, Path out)
      throws AnnException {
    String rendered = substitute(read(template), values, template);
    write(out, rendered);
    return out;
  }

  private static String read(Path template) throws AnnException {
    try {
      return Files.readString(template, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AnnException(
          AnnException.Kind.NOT_FOUND, "cannot read Ann template " + template, e);
    }
  }

  private static String substitute(String source, Map<String, String> values, Path template)
      throws AnnException {
    Matcher matcher = PLACEHOLDER.matcher(source);
    StringBuilder rendered = new StringBuilder(source.length());
    while (matcher.find()) {
      String key = matcher.group(1);
      String value = values.get(key);
      if (value == null) {
        throw new AnnException(
            AnnException.Kind.RENDER, "placeholder {{" + key + "}} has no value in " + template);
      }
      matcher.appendReplacement(rendered, Matcher.quoteReplacement(escape(key, value)));
    }
    matcher.appendTail(rendered);
    String result = rendered.toString();
    if (result.contains(OPEN)) {
      throw new AnnException(
          AnnException.Kind.RENDER, "malformed placeholder left after rendering " + template);
    }
    return result;
  }

  private static String escape(String key, String value) throws AnnException {
    if (value.contains(OPEN) || value.contains(CLOSE)) {
      throw new AnnException(
          AnnException.Kind.RENDER, "value of " + key + " contains {{ or }}, refused");
    }
    return value.replace("\"", "\\\"");
  }

  private static void write(Path out, String rendered) throws AnnException {
    try {
      Path parent = out.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(out, rendered, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AnnException(AnnException.Kind.RENDER, "cannot write Ann program " + out, e);
    }
  }
}
