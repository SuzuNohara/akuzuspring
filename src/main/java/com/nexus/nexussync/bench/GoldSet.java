package com.nexus.nexussync.bench;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.nexus.nexussync.params.ParamsException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loader of the calibration gold set (Fase C1, U13-01).
 *
 * <p>Every {@code *.yml} file of the gold directory labels one couple:
 *
 * <pre>
 * couple_id: "101-102"          # required: the coupleId of the runs
 * expected_types: [PARK, CAFE]  # required, at least one
 * forbidden_types: [BAR]        # optional, default []
 * forbidden_ids: [a-042]        # optional, default []
 * notes: "..."                  # optional, default ""
 * reviewed_by: "Suzu"           # optional: absent = draft (D-CAL-1)
 * </pre>
 *
 * <p>Any other key, a {@code reviewed_by} that is not a non-blank text, a missing or empty {@code
 * expected_types}, a non-list set, a non-text item or two files labelling the same couple are
 * errors. Couples without a file are simply absent from the result.
 */
public final class GoldSet {

  private static final YAMLMapper YAML = new YAMLMapper();
  private static final String COUPLE_ID = "couple_id";
  private static final String EXPECTED = "expected_types";
  private static final String FORBIDDEN_TYPES = "forbidden_types";
  private static final String FORBIDDEN_IDS = "forbidden_ids";
  private static final String NOTES = "notes";
  private static final String REVIEWED_BY = "reviewed_by";
  private static final Set<String> KEYS =
      Set.of(COUPLE_ID, EXPECTED, FORBIDDEN_TYPES, FORBIDDEN_IDS, NOTES, REVIEWED_BY);

  private GoldSet() {}

  /**
   * Loads every gold file of {@code goldDir}.
   *
   * @param goldDir directory holding {@code <slug>.yml} files
   * @return the entries by {@code coupleId}, in file-name order; unmodifiable
   * @throws ParamsException if the directory cannot be listed or a file is unreadable or invalid
   * @implNote O(F · s) time and space, F files, s size of a file.
   */
  public static Map<String, GoldEntry> load(Path goldDir) throws ParamsException {
    Map<String, GoldEntry> out = new LinkedHashMap<>();
    for (Path file : files(goldDir)) {
      GoldEntry e = entry(file);
      if (out.putIfAbsent(e.coupleId(), e) != null) {
        throw new ParamsException("duplicate gold for couple " + e.coupleId() + " in " + file);
      }
    }
    return Collections.unmodifiableMap(out);
  }

  private static List<Path> files(Path goldDir) throws ParamsException {
    try (Stream<Path> list = Files.list(goldDir)) {
      return list.filter(f -> f.getFileName().toString().endsWith(".yml"))
          .filter(Files::isRegularFile)
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new ParamsException("cannot list gold directory " + goldDir, e);
    }
  }

  private static GoldEntry entry(Path file) throws ParamsException {
    JsonNode root = read(file);
    for (Iterator<String> it = root.fieldNames(); it.hasNext(); ) {
      String key = it.next();
      if (!KEYS.contains(key)) {
        throw new ParamsException("unknown key '" + key + "' in " + file);
      }
    }
    JsonNode id = root.path(COUPLE_ID);
    if (!id.isTextual() || id.asText().isBlank()) {
      throw new ParamsException("missing couple_id in " + file);
    }
    Set<String> expected = texts(root, EXPECTED, file);
    if (expected.isEmpty()) {
      throw new ParamsException("expected_types must list at least one type in " + file);
    }
    JsonNode notes = root.path(NOTES);
    return new GoldEntry(
        id.asText(),
        expected,
        texts(root, FORBIDDEN_TYPES, file),
        texts(root, FORBIDDEN_IDS, file),
        notes.isMissingNode() || notes.isNull() ? "" : notes.asText(),
        reviewedBy(root, file));
  }

  private static Optional<String> reviewedBy(JsonNode root, Path file) throws ParamsException {
    JsonNode node = root.path(REVIEWED_BY);
    if (node.isMissingNode() || node.isNull()) {
      return Optional.empty();
    }
    if (!node.isTextual() || node.asText().isBlank()) {
      throw new ParamsException(REVIEWED_BY + " must be a non-blank text in " + file);
    }
    return Optional.of(node.asText());
  }

  private static JsonNode read(Path file) throws ParamsException {
    try {
      JsonNode root = YAML.readTree(file.toFile());
      if (root == null || !root.isObject()) {
        throw new ParamsException("gold file is not a mapping: " + file);
      }
      return root;
    } catch (JsonProcessingException e) {
      throw new ParamsException("invalid yaml in " + file, e);
    } catch (IOException e) {
      throw new ParamsException("cannot read " + file, e);
    }
  }

  private static Set<String> texts(JsonNode root, String key, Path file) throws ParamsException {
    JsonNode node = root.path(key);
    if (node.isMissingNode() || node.isNull()) {
      return Set.of();
    }
    if (!node.isArray()) {
      throw new ParamsException(key + " must be a list in " + file);
    }
    Set<String> out = new LinkedHashSet<>();
    for (JsonNode item : node) {
      if (!item.isValueNode() || item.isNull()) {
        throw new ParamsException(key + " must hold plain values in " + file);
      }
      out.add(item.asText());
    }
    return out;
  }
}
