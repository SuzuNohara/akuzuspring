package com.nexus.nexussync.params;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Canonical textual form of a parameter object and its short hash (unit U1, T-10).
 *
 * <p>The canonical form is the flow (JSON) rendering of the YAML: one line, keys in snake_case and
 * sorted alphabetically at every level, map entries ordered by key, no comments and no whitespace.
 * Paths are written as plain strings (never as URIs, so a relative {@code catalog_dir} does not
 * depend on the working directory) and empty {@code Optional}s as {@code null}. The hash is the
 * SHA-256 of that text encoded in UTF-8, truncated to its first {@value #HASH_LENGTH} hex digits.
 */
final class CanonicalYaml {

  static final int HASH_LENGTH = 8;
  private static final String ALGORITHM = "SHA-256";
  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .addModule(new Jdk8Module())
          .addModule(new SimpleModule().addSerializer(Path.class, ToStringSerializer.instance))
          .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .build();

  private CanonicalYaml() {}

  /**
   * Renders {@code o} in its canonical form.
   *
   * @param o a record, map or scalar; normally a {@link Params}
   * @return the single-line canonical text
   * @throws IllegalStateException if Jackson cannot serialize the object (programming error)
   * @implNote O(n log n) time in the number of keys because of the sorting; O(n) space.
   */
  static String canonical(Object o) {
    return canonical(o, Set.of());
  }

  /**
   * Renders {@code o} in its canonical form without the given top-level keys.
   *
   * @param o a record, map or scalar; normally a {@link Params}
   * @param excludedKeys top-level YAML keys left out, e.g. {@code runtime}; nested keys are kept
   * @return the single-line canonical text
   * @throws IllegalStateException if Jackson cannot serialize the object (programming error)
   * @implNote O(n log n) time in the number of keys because of the sorting; O(n) space.
   */
  static String canonical(Object o, Set<String> excludedKeys) {
    try {
      JsonNode tree = MAPPER.valueToTree(o);
      if (tree instanceof ObjectNode object) {
        object.remove(excludedKeys);
      }
      return MAPPER.writeValueAsString(tree);
    } catch (IllegalArgumentException | JsonProcessingException e) {
      throw new IllegalStateException(
          "no se puede canonizar " + o.getClass().getSimpleName() + ": " + e.getMessage(), e);
    }
  }

  /**
   * First {@value #HASH_LENGTH} hex digits of the SHA-256 of the canonical form of {@code o}.
   *
   * @param o the object to hash
   * @return {@value #HASH_LENGTH} lowercase hex characters
   * @implNote O(n log n) time and O(n) space, dominated by {@link #canonical(Object)}.
   */
  static String hash(Object o) {
    return hash(o, Set.of());
  }

  /**
   * First {@value #HASH_LENGTH} hex digits of the SHA-256 of the canonical form of {@code o}
   * without the given top-level keys.
   *
   * @param o the object to hash
   * @param excludedKeys top-level YAML keys that do not take part in the hash
   * @return {@value #HASH_LENGTH} lowercase hex characters
   * @implNote O(n log n) time and O(n) space, dominated by {@link #canonical(Object, Set)}.
   */
  static String hash(Object o, Set<String> excludedKeys) {
    return digest(canonical(o, excludedKeys)).substring(0, HASH_LENGTH);
  }

  /**
   * Full SHA-256 of a text, as lowercase hex.
   *
   * @param text the text to digest, encoded as UTF-8
   * @return 64 lowercase hex characters
   * @implNote O(n) time in the length of the text; O(1) extra space.
   */
  static String digest(String text) {
    try {
      MessageDigest sha = MessageDigest.getInstance(ALGORITHM);
      return HexFormat.of().formatHex(sha.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(ALGORITHM + " no disponible en esta JVM", e);
    }
  }
}
