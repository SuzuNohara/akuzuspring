package com.nexus.nexussync.ann;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** {@link RunIds}: {@code <exp>-<couple>-<epochMillis>-<seed>} with a slugged experiment (A7). */
class RunIdsTest {

  private static final long EPOCH = 1_758_672_000_000L;

  // U6-09
  @Test
  void given_plainExperiment_when_of_then_fourDashSeparatedParts() {
    String id = RunIds.of("baseline", "4-8", EPOCH, 42L);

    assertThat(id).isEqualTo("baseline-4-8-1758672000000-42");
  }

  // U6-09
  @Test
  void given_mixedCaseAndSymbols_when_of_then_experimentSlugged() {
    String id = RunIds.of("  Baseline (Haiku) v0.1_A!  ", "a-b", EPOCH, 7L);

    assertThat(id).isEqualTo("baseline-haiku-v0-1-a-a-b-1758672000000-7");
  }

  // U6-09
  @Test
  void given_nonAsciiExperiment_when_of_then_onlyLowercaseAsciiAndDigitsKept() {
    String id = RunIds.of("Calibración Ñ 2", "x-y", 0L, 0L);

    assertThat(id).isEqualTo("calibraci-n-2-x-y-0-0");
    assertThat(id).matches("[a-z0-9-]+");
  }

  // U6-09
  @Test
  void given_alreadySlug_when_of_then_experimentUnchanged() {
    String id = RunIds.of("cal-01", "min-max", 5L, -3L);

    assertThat(id).isEqualTo("cal-01-min-max-5--3");
  }

  // U6-09
  @Test
  void given_experimentWithoutAlphanumerics_when_of_then_rejected() {
    assertThatThrownBy(() -> RunIds.of("---", "a-b", EPOCH, 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RunIds.of("", "a-b", EPOCH, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // U6-09
  @Test
  void given_sameInputs_when_of_then_deterministic() {
    assertThat(RunIds.of("Exp", "a-b", EPOCH, 9L)).isEqualTo(RunIds.of("exp", "a-b", EPOCH, 9L));
  }
}
