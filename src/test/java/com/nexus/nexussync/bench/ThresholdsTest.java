package com.nexus.nexussync.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.params.ParamsException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThresholdsTest {

  private static final String VALID =
      "gold_violation_rate_max: 0.0\n"
          + "fairness_gap_max: 0.15\n"
          + "gold_hit_rate_min: 0.70\n"
          + "hallucination_rate_max: 0.02\n"
          + "stability_at_seed_min: 0.40\n"
          + "closure_f1_rate_min: 0.50\n"
          + "chosen_in_top3_truth_rate_min: 0.60\n";

  @TempDir Path dir;

  // C2 (D-CAL-2)
  @Test
  void given_thresholdsFile_when_load_then_everyKeyRead() throws Exception {
    Thresholds t = Thresholds.load(GoldSetTest.resource("thresholds.yml"));

    assertThat(t).isEqualTo(new Thresholds(0.0, 0.15, 0.70, 0.02, 0.40, 0.50, 0.60));
    assertThat(Thresholds.KEYS).hasSize(7);
  }

  @Test
  void given_invalidFiles_when_load_then_paramsException() throws Exception {
    List<String> invalid =
        List.of(
            VALID.replace("fairness_gap_max: 0.15\n", ""),
            VALID + "extra_key: 0.1\n",
            VALID.replace("0.15", "1.5"),
            VALID.replace("0.15", "-0.1"),
            VALID.replace("0.15", "\"high\""),
            VALID.replace("0.15", ".nan"),
            "- a\n- list\n",
            "",
            "gold_violation_rate_max: [unclosed\n");
    for (int i = 0; i < invalid.size(); i++) {
      Path file =
          Files.writeString(dir.resolve(i + ".yml"), invalid.get(i), StandardCharsets.UTF_8);

      assertThatThrownBy(() -> Thresholds.load(file))
          .as(invalid.get(i))
          .isInstanceOf(ParamsException.class);
    }
    assertThatThrownBy(() -> Thresholds.load(dir.resolve("missing.yml")))
        .isInstanceOf(ParamsException.class);
  }
}
