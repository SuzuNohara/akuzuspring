package com.nexus.nexussync.ann;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the {@code .output/<runId>.md} file that arkannie writes for a run (unit U6).
 *
 * <p>Format measured in the T-06 spike (arkannie 0.3.0): a yaml front matter between {@code ---}
 * lines with {@code id}, {@code agent}, {@code status}, {@code started}, {@code finished} and
 * {@code input}, followed by a body with one {@code ## r-N} section per finished dispatch, each
 * holding one fenced {@code yaml} block {@code {id, status, payload}}. Sections are written in
 * completion order, so envelopes are indexed by the {@code id} of the block, never by the section
 * name. A body without the {@code m} block is not an error: the caller decides what a missing agent
 * means.
 */
public final class OutputReader {

  private static final String OUTPUT_DIR = ".output";
  private static final String OUTPUT_EXT = ".md";
  private static final String FRONT_MATTER_FENCE = "---";
  private static final String YAML_FENCE_OPEN = "```yaml";
  private static final String YAML_FENCE_CLOSE = "```";
  private static final String ID_KEY = "id";
  private static final String STATUS_KEY = "status";
  private static final String PAYLOAD_KEY = "payload";
  private static final String SCALAR_PAYLOAD_KEY = "value";

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
  private static final JavaType MAP_TYPE =
      YAML.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class);

  private OutputReader() {}

  /**
   * Parses {@code nexussyncDir/.output/<runId>.md} into a {@link RunOutput} without process data.
   *
   * @param nexussyncDir root of the nexussync home ({@code ARKANNIE_HOME}), never {@code null}
   * @param runId run identifier, i.e. the {@code --id} given to arkannie, never {@code null}
   * @return the run status and the envelopes indexed by dispatch id; streams empty, elapsed zero
   * @throws AnnException {@code NOT_FOUND} if the file does not exist; {@code PARSE} if the front
   *     matter is unterminated or invalid yaml, lacks {@code status}, or a yaml block of the body
   *     is invalid, has no {@code id} or repeats one
   * @implNote O(n) time and space, n = size of the output file.
   */
  public static RunOutput read(Path nexussyncDir, String runId) throws AnnException {
    Path file = nexussyncDir.resolve(OUTPUT_DIR).resolve(runId + OUTPUT_EXT);
    if (!Files.isRegularFile(file)) {
      throw new AnnException(AnnException.Kind.NOT_FOUND, "arkannie output not found: " + file);
    }
    List<String> lines = readLines(file);
    int close = frontMatterEnd(lines, file);
    Map<String, Object> front = parseYaml(lines.subList(1, close), "front matter of " + file);
    Object status = front.get(STATUS_KEY);
    if (status == null) {
      throw new AnnException(
          AnnException.Kind.PARSE, "front matter of " + file + " has no '" + STATUS_KEY + "'");
    }
    Map<String, Envelope> envelopes = readBody(lines.subList(close + 1, lines.size()), file);
    return new RunOutput(runId, String.valueOf(status), envelopes, "", "", Duration.ZERO);
  }

  private static List<String> readLines(Path file) throws AnnException {
    try {
      return Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AnnException(
          AnnException.Kind.PARSE, "arkannie output could not be read: " + file, e);
    }
  }

  /** Returns the index of the closing {@code ---}; the opening one must be the first line. */
  private static int frontMatterEnd(List<String> lines, Path file) throws AnnException {
    if (lines.isEmpty() || !FRONT_MATTER_FENCE.equals(lines.get(0).strip())) {
      throw new AnnException(
          AnnException.Kind.PARSE, "arkannie output does not start with front matter: " + file);
    }
    for (int i = 1; i < lines.size(); i++) {
      if (FRONT_MATTER_FENCE.equals(lines.get(i).strip())) {
        return i;
      }
    }
    throw new AnnException(
        AnnException.Kind.PARSE, "front matter of " + file + " has no closing '---'");
  }

  /** Collects every fenced yaml block of the body as an envelope, indexed by its {@code id}. */
  private static Map<String, Envelope> readBody(List<String> body, Path file) throws AnnException {
    Map<String, Envelope> envelopes = new LinkedHashMap<>();
    List<String> block = new ArrayList<>();
    boolean inBlock = false;
    for (String line : body) {
      String stripped = line.strip();
      if (!inBlock && YAML_FENCE_OPEN.equals(stripped)) {
        inBlock = true;
      } else if (inBlock && YAML_FENCE_CLOSE.equals(stripped)) {
        addEnvelope(envelopes, toEnvelope(block, file), file);
        block = new ArrayList<>();
        inBlock = false;
      } else if (inBlock) {
        block.add(line);
      }
    }
    if (inBlock) {
      throw new AnnException(
          AnnException.Kind.PARSE, "unterminated yaml block in arkannie output: " + file);
    }
    return envelopes;
  }

  private static void addEnvelope(Map<String, Envelope> envelopes, Envelope envelope, Path file)
      throws AnnException {
    if (envelopes.putIfAbsent(envelope.id(), envelope) != null) {
      throw new AnnException(
          AnnException.Kind.PARSE,
          "arkannie output " + file + " repeats envelope id '" + envelope.id() + "'");
    }
  }

  private static Envelope toEnvelope(List<String> block, Path file) throws AnnException {
    Map<String, Object> fields = parseYaml(block, "yaml block of " + file);
    Object id = fields.get(ID_KEY);
    if (id == null) {
      throw new AnnException(
          AnnException.Kind.PARSE, "yaml block without '" + ID_KEY + "' in " + file);
    }
    Object status = fields.get(STATUS_KEY);
    String statusText = status == null ? "" : String.valueOf(status);
    return new Envelope(String.valueOf(id), statusText, payloadOf(fields.get(PAYLOAD_KEY)));
  }

  private static Map<String, Object> payloadOf(Object payload) {
    if (payload == null) {
      return Map.of();
    }
    if (payload instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((key, value) -> copy.put(String.valueOf(key), value));
      return copy;
    }
    Map<String, Object> scalar = new LinkedHashMap<>();
    scalar.put(SCALAR_PAYLOAD_KEY, payload);
    return scalar;
  }

  private static Map<String, Object> parseYaml(List<String> lines, String what)
      throws AnnException {
    try {
      Map<String, Object> parsed = YAML.readValue(String.join("\n", lines), MAP_TYPE);
      return parsed == null ? new LinkedHashMap<>() : parsed;
    } catch (JsonProcessingException e) {
      throw new AnnException(AnnException.Kind.PARSE, "invalid yaml in " + what, e);
    }
  }
}
