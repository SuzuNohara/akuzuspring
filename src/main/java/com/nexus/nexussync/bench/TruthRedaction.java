package com.nexus.nexussync.bench;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.ContextException;
import com.nexus.nexussync.context.Profile;
import com.nexus.nexussync.context.ProfileLoader;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.rounds.Agent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the hidden truth out of every file under {@code runs/} (D-33).
 *
 * <p>The agents can read the file system, so the {@code truthWeights} of the profiles are never
 * written with the traces: {@code context.json} carries, per person, {@value #CONTEXT_KEY} (the
 * truncated SHA-256 of the weights, {@link #hash}) instead of the weights, and {@code record.json}
 * carries {@value #RECORD_KEY} (the same hash per person) instead of {@code Evidence.truthWeights}.
 * The metrics that need the truth compute it in memory during the run ({@code Sweep}); a record
 * read back from disk ({@link Comparer}) gets its truth again only from the couple fixtures, and
 * only when their hash matches the recorded one.
 */
final class TruthRedaction {

  /** Key replacing {@code truthWeights} in each profile of {@code context.json}. */
  static final String CONTEXT_KEY = "truthHash";

  /** Key replacing {@code truthWeights} in the evidence of {@code record.json}. */
  static final String RECORD_KEY = "truthHashes";

  /** Hex characters kept from the SHA-256 digest. */
  static final int HASH_CHARS = 16;

  private static final String WEIGHTS = "truthWeights";
  private static final String EVIDENCE_POINTER = "/evidence";
  private static final Logger LOG = LoggerFactory.getLogger(TruthRedaction.class);

  private TruthRedaction() {}

  /**
   * Truncated SHA-256 of a weight vector, over {@code FEATURE=value;} in feature order (features
   * absent from {@code w} are skipped).
   *
   * @param w weights
   * @return the first {@value #HASH_CHARS} hex characters of the digest
   * @implNote O(f) time and space, f features.
   */
  static String hash(Map<Feature, Double> w) {
    StringBuilder canonical = new StringBuilder();
    for (Feature f : Feature.values()) {
      if (w.containsKey(f)) {
        canonical.append(f.name()).append('=').append(w.get(f)).append(';');
      }
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, HASH_CHARS);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory in every JVM", e);
    }
  }

  /**
   * JSON tree of a context whose profiles carry {@value #CONTEXT_KEY} instead of {@code
   * truthWeights} ({@code null} for a person without them).
   *
   * @param ctx context of the couple
   * @return the redacted tree
   * @implNote O(size of the context) time and space.
   */
  static JsonNode context(Context ctx) {
    ObjectNode tree = BenchIo.JSON.valueToTree(ctx);
    redactProfile(tree.withObject("/a"), ctx.a());
    redactProfile(tree.withObject("/b"), ctx.b());
    return tree;
  }

  /**
   * JSON tree of a record whose evidence carries {@value #RECORD_KEY} instead of {@code
   * truthWeights}.
   *
   * @param r run record
   * @return the redacted tree
   * @implNote O(size of the record) time and space.
   */
  static JsonNode record(RunRecord r) {
    ObjectNode tree = BenchIo.JSON.valueToTree(r);
    ObjectNode evidence = tree.withObject(EVIDENCE_POINTER);
    evidence.remove(WEIGHTS);
    ObjectNode hashes = evidence.putObject(RECORD_KEY);
    r.evidence().truthWeights().forEach((agent, w) -> hashes.put(agent.name(), hash(w)));
    return tree;
  }

  /**
   * Binds a redacted {@code record.json}; the truth of each person is restored from {@code truth}
   * only when its hash equals the recorded one, otherwise that person has no truth.
   *
   * @param tree the redacted record
   * @param truth truth weights by couple id and person (see {@link #fixtureTruth})
   * @return the record
   * @throws JsonProcessingException if the tree is not a record
   * @implNote O(size of the record) time and space.
   */
  static RunRecord read(JsonNode tree, Map<String, Map<Agent, Map<Feature, Double>>> truth)
      throws JsonProcessingException {
    ObjectNode copy = tree.deepCopy();
    Map<Agent, String> hashes = new EnumMap<>(Agent.class);
    ObjectNode evidence = copy.withObject(EVIDENCE_POINTER);
    JsonNode recorded = evidence.remove(RECORD_KEY);
    if (recorded != null) {
      recorded.fields().forEachRemaining(e -> hashes.put(Agent.valueOf(e.getKey()), text(e)));
    }
    evidence.putObject(WEIGHTS);
    RunRecord r = BenchIo.JSON.treeToValue(copy, RunRecord.class);
    Map<Agent, Map<Feature, Double>> known = truth.getOrDefault(r.coupleId(), Map.of());
    Map<Agent, Map<Feature, Double>> restored = new EnumMap<>(Agent.class);
    hashes.forEach(
        (agent, h) ->
            Optional.ofNullable(known.get(agent))
                .filter(w -> hash(w).equals(h))
                .ifPresentOrElse(
                    w -> restored.put(agent, w),
                    () -> LOG.warn("no matching truth for {} {}", r.coupleId(), agent)));
    return restored.isEmpty() ? r : withTruth(r, restored);
  }

  /**
   * Truth weights of every couple file of a directory, by couple id ({@code min-max} of the user
   * ids) and person; a missing directory has none.
   *
   * @param couplesDir directory of {@code <slug>.json} couple files
   * @return the truth of the couples that have it
   * @throws UncheckedIOException if the directory cannot be listed or a couple file is invalid
   * @implNote O(c · size of a couple file) time and O(c) space, c couple files.
   */
  static Map<String, Map<Agent, Map<Feature, Double>>> fixtureTruth(Path couplesDir) {
    if (!Files.isDirectory(couplesDir)) {
      return Map.of();
    }
    List<Path> files;
    try (Stream<Path> list = Files.list(couplesDir)) {
      files = list.filter(f -> f.toString().endsWith(".json")).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException("cannot list " + couplesDir, e);
    }
    Map<String, Map<Agent, Map<Feature, Double>>> out = new HashMap<>();
    for (Path file : files) {
      try {
        ProfileLoader.CoupleFile c = ProfileLoader.loadCouple(file);
        Map<Agent, Map<Feature, Double>> t = new EnumMap<>(Agent.class);
        c.a().truthWeights().ifPresent(w -> t.put(Agent.A, w));
        c.b().truthWeights().ifPresent(w -> t.put(Agent.B, w));
        int x = c.a().userId();
        int y = c.b().userId();
        out.put(Math.min(x, y) + "-" + Math.max(x, y), t);
      } catch (ContextException e) {
        throw new UncheckedIOException(new IOException("invalid couple " + file, e));
      }
    }
    return out;
  }

  private static void redactProfile(ObjectNode profile, Profile p) {
    profile.remove(WEIGHTS);
    profile.put(CONTEXT_KEY, p.truthWeights().map(TruthRedaction::hash).orElse(null));
  }

  private static String text(Map.Entry<String, JsonNode> e) {
    return e.getValue().asText();
  }

  private static RunRecord withTruth(RunRecord r, Map<Agent, Map<Feature, Double>> truth) {
    RunRecord.Evidence e = r.evidence();
    return new RunRecord(
        r.runId(),
        r.coupleId(),
        r.experiment(),
        r.paramsHash(),
        r.seed(),
        r.sample(),
        r.gate(),
        r.decision(),
        r.places(),
        r.before(),
        r.after(),
        r.elapsed(),
        new RunRecord.Evidence(
            truth,
            e.intersectionF1(),
            e.finalTypes(),
            e.hoursDeclared(),
            e.hoursParsed(),
            e.calls()));
  }
}
