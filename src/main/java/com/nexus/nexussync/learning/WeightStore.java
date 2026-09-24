package com.nexus.nexussync.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Versioned YAML persistence of the learned weights: one file {@code <coupleId>.v<version>.yml} per
 * version inside the weights directory (normally {@code runs/weights/}).
 */
public final class WeightStore {

  private static final Logger LOG = LoggerFactory.getLogger(WeightStore.class);
  private static final ObjectMapper YAML = new YAMLMapper();
  private static final Pattern COUPLE_ID = Pattern.compile("[A-Za-z0-9_-]+");
  private static final String SUFFIX = ".yml";

  private WeightStore() {}

  /**
   * Writes the weights to {@code dir/<coupleId>.v<version>.yml}, creating {@code dir} if needed and
   * overwriting an existing file of the same version.
   *
   * @param w the weights to save
   * @param dir the weights directory
   * @return the written file
   * @throws IOException if the directory or the file cannot be written
   * @throws IllegalArgumentException if the couple id is not a plain slug ({@code [A-Za-z0-9_-]+})
   * @implNote O(f) time and space, f = features.
   */
  public static Path save(Weights w, Path dir) throws IOException {
    Path file = dir.resolve(fileName(w.coupleId(), w.version()));
    Files.createDirectories(dir);
    YAML.writeValue(file.toFile(), w);
    return file;
  }

  /**
   * Loads the highest version saved for a couple. Weights learned under another parameter set
   * (different {@code paramsHash}, A5) are discarded with a warning, so the caller cold-starts.
   *
   * @param coupleId the couple
   * @param paramsHash hash of the current parameter set
   * @param dir the weights directory
   * @return the latest weights, or empty if there is none, the directory does not exist or the hash
   *     differs (a file without {@code paramsHash} counts as a different hash, D-25)
   * @throws IOException if the directory or the file cannot be read or parsed
   * @throws IllegalArgumentException if the couple id is not a plain slug
   * @implNote O(n + f) time, n = files in {@code dir}, f = features; O(f) space.
   */
  public static Optional<Weights> load(String coupleId, String paramsHash, Path dir)
      throws IOException {
    checkCoupleId(coupleId);
    if (!Files.isDirectory(dir)) {
      return Optional.empty();
    }
    OptionalInt latest = latestVersion(coupleId, dir);
    if (latest.isEmpty()) {
      return Optional.empty();
    }
    Path file = dir.resolve(fileName(coupleId, latest.getAsInt()));
    Weights stored = YAML.readValue(file.toFile(), Weights.class);
    if (stored.paramsHash() == null || !sameHash(stored.paramsHash(), paramsHash)) {
      LOG.warn(
          "pesos de {} descartados: paramsHash guardado {} ≠ actual {}; arranque en frío",
          coupleId,
          stored.paramsHash(),
          paramsHash);
      return Optional.empty();
    }
    return Optional.of(stored);
  }

  private static OptionalInt latestVersion(String coupleId, Path dir) throws IOException {
    Pattern name =
        Pattern.compile(Pattern.quote(coupleId + ".v") + "(\\d{1,9})" + Pattern.quote(SUFFIX));
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .map(p -> name.matcher(p.getFileName().toString()))
          .filter(Matcher::matches)
          .mapToInt(m -> Integer.parseInt(m.group(1)))
          .max();
    }
  }

  /**
   * Constant-time comparison: the hash is not a secret, but it keeps findsecbugs'
   * UNSAFE_HASH_EQUALS rule satisfied without a suppression.
   */
  private static boolean sameHash(String stored, String requested) {
    return MessageDigest.isEqual(
        stored.getBytes(StandardCharsets.UTF_8), requested.getBytes(StandardCharsets.UTF_8));
  }

  private static String fileName(String coupleId, int version) {
    checkCoupleId(coupleId);
    return coupleId + ".v" + version + SUFFIX;
  }

  private static void checkCoupleId(String coupleId) {
    if (!COUPLE_ID.matcher(coupleId).matches()) {
      throw new IllegalArgumentException("coupleId no es un slug: " + coupleId);
    }
  }
}
