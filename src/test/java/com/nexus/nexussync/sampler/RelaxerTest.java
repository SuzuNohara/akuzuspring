package com.nexus.nexussync.sampler;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.params.FilterName;
import com.nexus.nexussync.sampler.Budget.PriceBand;
import com.nexus.nexussync.sampler.Relaxer.Relaxation;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** One relaxation step (T-30). */
final class RelaxerTest {

  // U4-13
  @Test
  void given_budgetStep_when_apply_then_oneBandUp() {
    assertThat(Relaxer.apply(FilterName.BUDGET, 15.0, PriceBand.LOW))
        .isEqualTo(new Relaxation(15.0, PriceBand.MID, Optional.empty()));
    assertThat(Relaxer.apply(FilterName.BUDGET, 15.0, PriceBand.PREMIUM).budget())
        .isEqualTo(PriceBand.PREMIUM);
  }

  // U4-13
  @Test
  void given_radiusStep_when_apply_then_fiveMoreKm() {
    assertThat(Relaxer.apply(FilterName.RADIUS, 15.0, PriceBand.LOW))
        .isEqualTo(new Relaxation(20.0, PriceBand.LOW, Optional.empty()));
  }

  // U4-13
  @Test
  void given_switchStep_when_apply_then_filterDisabled() {
    assertThat(Relaxer.apply(FilterName.COOLDOWN, 15.0, PriceBand.LOW))
        .isEqualTo(new Relaxation(15.0, PriceBand.LOW, Optional.of(FilterName.COOLDOWN)));
    assertThat(Relaxer.apply(FilterName.WEATHER, 15.0, PriceBand.LOW).disabled())
        .contains(FilterName.WEATHER);
  }
}
