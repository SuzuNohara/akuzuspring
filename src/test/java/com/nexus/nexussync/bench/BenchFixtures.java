package com.nexus.nexussync.bench;

import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.CatalogLoader;
import com.nexus.nexussync.context.Climate;
import com.nexus.nexussync.context.Constraints;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.Level;
import com.nexus.nexussync.context.Profile;
import com.nexus.nexussync.learning.Weights;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.params.ParamsLoader;
import com.nexus.nexussync.params.RuntimeParams;
import com.nexus.nexussync.rounds.Closure;
import com.nexus.nexussync.rounds.GateResult;
import com.nexus.nexussync.rounds.Pick;
import com.nexus.nexussync.sampler.Sample;
import com.nexus.nexussync.sampler.SampleStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;

/** Shared inputs of the bench tests; nothing reads the system clock. */
final class BenchFixtures {

  /** Start of the ticking clock: 2026-09-24T00:00:00Z. */
  static final long EPOCH = 1_790_208_000_000L;

  private BenchFixtures() {}

  /** Root {@code nexussync/} from {@code -Dnexussync.dir}; skips the test when absent. */
  static Path nexussyncDir() {
    String dir = System.getProperty("nexussync.dir", "");
    Path root = Path.of(dir.isEmpty() ? "missing-nexussync-dir" : dir);
    Assumptions.assumeTrue(
        Files.isDirectory(root.resolve("fixtures")), "nexussync.dir no definido o sin fixtures");
    return root;
  }

  /** {@code baseline-haiku} with the runtime home moved to {@code home} (lock and rubric). */
  static Params params(Path nx, Path home) throws Exception {
    Params p =
        ParamsLoader.load(
            nx.resolve("params").resolve("baseline-haiku.yml"),
            nx.resolve("params").resolve("default.yml"));
    RuntimeParams rt = p.runtime();
    RuntimeParams moved =
        new RuntimeParams(
            rt.executor(),
            home,
            rt.arkannieBin(),
            rt.replayDir(),
            rt.maxCalls(),
            rt.arkannieVersion());
    return withRuntime(p, moved);
  }

  static Params withRuntime(Params p, RuntimeParams rt) {
    return new Params(
        p.experiment(),
        p.seed(),
        p.catalogDir(),
        p.placesCsv(),
        p.sampler(),
        p.rounds(),
        p.agents(),
        p.decision(),
        p.place(),
        p.learning(),
        p.context(),
        rt,
        p.rubric(),
        p.bench());
  }

  static Params withExperiment(Params p, String experiment) {
    return new Params(
        experiment,
        p.seed(),
        p.catalogDir(),
        p.placesCsv(),
        p.sampler(),
        p.rounds(),
        p.agents(),
        p.decision(),
        p.place(),
        p.learning(),
        p.context(),
        p.runtime(),
        p.rubric(),
        p.bench());
  }

  /** Synthetic catalog of {@code nexussync/fixtures/catalog-synthetic}. */
  static Catalog catalog(Path nx, Params p) throws Exception {
    return CatalogLoader.load(nx.resolve(p.catalogDir()), nx.resolve(p.placesCsv()));
  }

  /** Couple file {@code fixtures/couples/<slug>.json}. */
  static Path couple(Path nx, String slug) {
    return nx.resolve("fixtures").resolve("couples").resolve(slug + ".json");
  }

  /** Clock that advances one second on every read, starting at {@link #EPOCH}. */
  static Clock ticking() {
    return new TickingClock();
  }

  /** Profile with neutral data and the given truth weights. */
  static Profile profile(int userId, Optional<Map<Feature, Double>> truth) {
    return new Profile(
        userId,
        "Coyoacan",
        Optional.empty(),
        Map.of(),
        List.of(),
        List.of(),
        new Constraints(
            "MID", "NEAR", DayOfWeek.SATURDAY, LocalTime.of(10, 0), LocalTime.of(14, 0)),
        truth);
  }

  /** Context of two neutral profiles with the given truth weights. */
  static Context context(
      Optional<Map<Feature, Double>> truthA, Optional<Map<Feature, Double>> truthB) {
    Climate unknown = new Climate(Level.UNKNOWN, Level.UNKNOWN);
    return new Context(
        "1-2",
        profile(1, truthA),
        profile(2, truthB),
        unknown,
        unknown,
        List.of(),
        Map.of(),
        0,
        LocalDate.of(2026, 9, 27));
  }

  static Weights weights(Map<Feature, Double> w) {
    return new Weights("1-2", "abcd1234", w, 1, 1);
  }

  static GateResult gate(Closure closure, List<String> finalIds, List<Pick> r1, List<Pick> r2) {
    return new GateResult(finalIds, closure, List.of(), r1, r2, Map.of(), Map.of());
  }

  static Sample emptySample() {
    return new Sample(List.of(), List.of(), SampleStatus.OK, Map.of());
  }

  /** Record with the given outcome; unrelated components are neutral. */
  static RunRecord record(
      GateResult gate,
      Sample sample,
      Optional<Weights> after,
      Duration elapsed,
      RunRecord.Evidence evidence) {
    return new RunRecord(
        "exp-1-2-" + EPOCH + "-42",
        "1-2",
        "exp",
        "abcd1234",
        42L,
        sample,
        gate,
        Optional.empty(),
        Optional.empty(),
        weights(Map.of(Feature.INTEREST, 1.0)),
        after,
        elapsed,
        evidence);
  }

  static RunRecord.Evidence noEvidence() {
    return new RunRecord.Evidence(Map.of(), 0, List.of(), 0, 0);
  }

  /** Clock advancing one second per read; tests are single-threaded. */
  private static final class TickingClock extends Clock {
    private long millis = EPOCH;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      Instant now = Instant.ofEpochMilli(millis);
      millis += 1_000L;
      return now;
    }
  }
}
