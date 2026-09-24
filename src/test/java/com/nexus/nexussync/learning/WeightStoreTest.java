package com.nexus.nexussync.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.params.Feature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Versioned persistence of the learned weights (T-58). */
final class WeightStoreTest {

  private static final String HASH = "abcd1234";

  private static Weights weights(int version, String hash, double interest) {
    Map<Feature, Double> w = WeightUpdaterTest.constant(0.1);
    w.put(Feature.INTEREST, interest);
    return new Weights("4-8", hash, w, version, version);
  }

  // U10-06
  @Test
  void given_versionTwo_when_save_then_versionedFileUnderWeightsDir(@TempDir Path tmp)
      throws Exception {
    Path dir = tmp.resolve("runs").resolve("weights");

    Path file = WeightStore.save(weights(2, HASH, 0.4), dir);

    assertThat(file).isEqualTo(dir.resolve("4-8.v2.yml")).isRegularFile();
  }

  // U10-06
  @Test
  void given_severalVersions_when_load_then_highestVersionRoundTrips(@TempDir Path tmp)
      throws Exception {
    Path dir = tmp.resolve("runs").resolve("weights");
    WeightStore.save(weights(2, HASH, 0.4), dir);
    WeightStore.save(weights(10, HASH, 0.3), dir);
    WeightStore.save(weights(9, HASH, 0.2), dir);
    WeightStore.save(new Weights("4-80", HASH, WeightUpdaterTest.constant(0.1), 99, 99), dir);
    Files.writeString(dir.resolve("4-8.vX.yml"), "basura", StandardCharsets.UTF_8);

    Optional<Weights> loaded = WeightStore.load("4-8", HASH, dir);

    assertThat(loaded).contains(weights(10, HASH, 0.3));
  }

  // U10-06
  @Test
  void given_otherParamsHash_when_load_then_empty(@TempDir Path tmp) throws Exception {
    WeightStore.save(weights(3, HASH, 0.4), tmp);

    assertThat(WeightStore.load("4-8", "ffff0000", tmp)).isEmpty();
  }

  // U10-06
  @Test
  void given_emptyOrMissingDirectory_when_load_then_empty(@TempDir Path tmp) throws Exception {
    assertThat(WeightStore.load("4-8", HASH, tmp)).isEmpty();
    assertThat(WeightStore.load("4-8", HASH, tmp.resolve("missing"))).isEmpty();
  }

  @Test
  void given_coupleIdWithPathSeparator_when_saveOrLoad_then_illegalArgument(@TempDir Path tmp) {
    Weights bad = new Weights("../4-8", HASH, WeightUpdaterTest.constant(0.1), 0, 0);

    assertThatThrownBy(() -> WeightStore.save(bad, tmp))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("../4-8");
    assertThatThrownBy(() -> WeightStore.load("a/b", HASH, tmp))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
