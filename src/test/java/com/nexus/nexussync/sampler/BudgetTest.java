package com.nexus.nexussync.sampler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.sampler.Budget.PriceBand;
import org.junit.jupiter.api.Test;

/** Budget bands and ceilings (T-23). */
final class BudgetTest {

  // U4-17
  @Test
  void given_everyBand_when_ceiling_then_tableValues() {
    assertThat(Budget.CEILING)
        .containsEntry(PriceBand.FREE, 0)
        .containsEntry(PriceBand.LOW, 200)
        .containsEntry(PriceBand.MID, 800)
        .containsEntry(PriceBand.HIGH, 3000)
        .containsEntry(PriceBand.PREMIUM, 10000)
        .hasSize(5);
    assertThat(Budget.ceiling(PriceBand.MID)).isEqualTo(800);
  }

  // U4-17
  @Test
  void given_band_when_up_then_nextBandAndPremiumStays() {
    assertThat(Budget.up(PriceBand.FREE)).isEqualTo(PriceBand.LOW);
    assertThat(Budget.up(PriceBand.HIGH)).isEqualTo(PriceBand.PREMIUM);
    assertThat(Budget.up(PriceBand.PREMIUM)).isEqualTo(PriceBand.PREMIUM);
  }

  @Test
  void given_twoBands_when_lowest_then_cheaperOneCaseInsensitive() {
    assertThat(Budget.lowest("high", " LOW ")).isEqualTo(PriceBand.LOW);
    assertThat(Budget.lowest("FREE", "MID")).isEqualTo(PriceBand.FREE);
    assertThat(Budget.lowest("MID", "MID")).isEqualTo(PriceBand.MID);
  }

  @Test
  void given_unknownBand_when_parse_then_illegalArgument() {
    assertThatThrownBy(() -> Budget.parse("CHEAP")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void given_ceilingMap_when_modified_then_unsupported() {
    assertThatThrownBy(() -> Budget.CEILING.put(PriceBand.FREE, 1))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
