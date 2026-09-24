package com.nexus.nexussync.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.rounds.Agent;
import com.nexus.nexussync.rounds.Closure;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** D-33: the truth weights never reach a file of {@code runs/}. */
class TruthRedactionTest {

  private static final Map<Feature, Double> INTEREST = Map.of(Feature.INTEREST, 1.0);
  private static final Map<Feature, Double> PRICE = Map.of(Feature.PRICE, 1.0);

  @TempDir Path tmp;

  private static RunRecord record(Map<Agent, Map<Feature, Double>> truth) {
    return BenchFixtures.record(
        BenchFixtures.gate(Closure.F1, List.of("x"), List.of(), List.of()),
        BenchFixtures.emptySample(),
        Optional.of(BenchFixtures.weights(INTEREST)),
        Duration.ofSeconds(1),
        new RunRecord.Evidence(truth, 0, List.of(), 0, 0, 0));
  }

  private static Map<Agent, Map<Feature, Double>> both(
      Map<Feature, Double> a, Map<Feature, Double> b) {
    Map<Agent, Map<Feature, Double>> t = new EnumMap<>(Agent.class);
    t.put(Agent.A, a);
    t.put(Agent.B, b);
    return t;
  }

  @Test
  void given_weights_when_hash_then_deterministicTruncatedAndSensitive() {
    Map<Feature, Double> w = new EnumMap<>(Feature.class);
    w.put(Feature.PRICE, 0.25);
    w.put(Feature.INTEREST, 0.75);

    String h = TruthRedaction.hash(w);

    assertThat(h).hasSize(TruthRedaction.HASH_CHARS).matches("[0-9a-f]+");
    assertThat(TruthRedaction.hash(Map.of(Feature.INTEREST, 0.75, Feature.PRICE, 0.25)))
        .isEqualTo(h);
    assertThat(TruthRedaction.hash(Map.of(Feature.INTEREST, 0.75, Feature.PRICE, 0.26)))
        .isNotEqualTo(h);
  }

  @Test
  void given_context_when_redacted_then_hashPerPersonAndNullWithoutTruth() {
    JsonNode tree =
        TruthRedaction.context(BenchFixtures.context(Optional.of(INTEREST), Optional.empty()));

    assertThat(tree.toString()).doesNotContain("truthWeights");
    assertThat(tree.path("a").path(TruthRedaction.CONTEXT_KEY).asText())
        .isEqualTo(TruthRedaction.hash(INTEREST));
    assertThat(tree.path("b").path(TruthRedaction.CONTEXT_KEY).isNull()).isTrue();
    assertThat(tree.path("a").path("userId").asInt()).isEqualTo(1);
  }

  @Test
  void given_record_when_redactedAndReadWithMatchingTruth_then_equalRecord() throws Exception {
    RunRecord r = record(both(INTEREST, PRICE));

    JsonNode tree = TruthRedaction.record(r);

    assertThat(tree.toString()).doesNotContain("truthWeights");
    assertThat(TruthRedaction.read(tree, Map.of("1-2", both(INTEREST, PRICE)))).isEqualTo(r);
  }

  @Test
  void given_partialMatch_when_read_then_onlyMatchingPersonRestored() throws Exception {
    JsonNode tree = TruthRedaction.record(record(both(INTEREST, PRICE)));

    RunRecord r = TruthRedaction.read(tree, Map.of("1-2", Map.of(Agent.A, INTEREST)));

    assertThat(r.evidence().truthWeights()).containsOnlyKeys(Agent.A);
    assertThat(TruthRedaction.read(tree, Map.of()).evidence().truthWeights()).isEmpty();
  }

  @Test
  void given_legacyRecordWithTruthAndNoHashes_when_read_then_truthDropped() throws Exception {
    ObjectNode legacy = BenchIo.JSON.valueToTree(record(both(INTEREST, PRICE)));

    RunRecord r = TruthRedaction.read(legacy, Map.of("1-2", both(INTEREST, PRICE)));

    assertThat(r.evidence().truthWeights()).isEmpty();
  }

  @Test
  void given_couplesDir_when_fixtureTruth_then_truthByCoupleId() {
    Path nx = BenchFixtures.nexussyncDir();

    Map<String, Map<Agent, Map<Feature, Double>>> truth =
        TruthRedaction.fixtureTruth(nx.resolve("fixtures/couples"));

    assertThat(truth).hasSizeGreaterThanOrEqualTo(10).containsKey("101-102");
    assertThat(truth.get("101-102")).containsOnlyKeys(Agent.A, Agent.B);
    assertThat(TruthRedaction.fixtureTruth(tmp.resolve("missing"))).isEmpty();
  }

  @Test
  void given_invalidCoupleFile_when_fixtureTruth_then_uncheckedIo() throws Exception {
    Files.writeString(tmp.resolve("notes.txt"), "ignored", StandardCharsets.UTF_8);
    assertThat(TruthRedaction.fixtureTruth(tmp)).isEmpty();
    Files.writeString(tmp.resolve("roto.json"), "{}", StandardCharsets.UTF_8);

    assertThatThrownBy(() -> TruthRedaction.fixtureTruth(tmp))
        .isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void given_couplesDirWithRealFixtures_when_compare_then_reportWritten() throws Exception {
    Path nx = BenchFixtures.nexussyncDir();
    Path exp = tmp.resolve("runs/exp");
    RunRecord r = record(both(INTEREST, PRICE));
    BenchIo.write(
        BenchIo.JSON,
        exp.resolve(r.coupleId()).resolve(r.runId()).resolve(CoupleRunner.RECORD),
        TruthRedaction.record(r));

    Path report =
        Comparer.compare(
            List.of(exp),
            tmp.resolve("reports"),
            LocalDate.of(2026, 9, 24),
            nx.resolve("fixtures/couples"));

    assertThat(Files.readString(report, StandardCharsets.UTF_8)).contains("| runs | 1 |");
  }
}
