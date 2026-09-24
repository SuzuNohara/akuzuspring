package com.nexus.nexussync.sampler;

import com.nexus.nexussync.params.FilterName;
import com.nexus.nexussync.sampler.Budget.PriceBand;
import java.util.Optional;

/**
 * One relaxation step of G2 (§3.4): BUDGET moves one band up, RADIUS adds {@value #RADIUS_STEP_KM}
 * km, and any other filter (COOLDOWN, WEATHER, ...) is switched off.
 */
final class Relaxer {

  /** Kilometres added to the radius by a RADIUS step. */
  static final double RADIUS_STEP_KM = 5.0;

  private Relaxer() {}

  /**
   * State of the relaxable parameters after a step.
   *
   * @param radiusKm radius to use from now on
   * @param budget budget band to use from now on
   * @param disabled filter switched off by the step, when the step disables one
   */
  record Relaxation(double radiusKm, PriceBand budget, Optional<FilterName> disabled) {}

  /**
   * Applies one relaxation step.
   *
   * @param step filter to relax
   * @param radiusKm current radius
   * @param budget current budget band
   * @return the relaxed radius and band, plus the filter to switch off for non-parametric steps
   * @implNote O(1) time and space.
   */
  static Relaxation apply(FilterName step, double radiusKm, PriceBand budget) {
    return switch (step) {
      case BUDGET -> new Relaxation(radiusKm, Budget.up(budget), Optional.empty());
      case RADIUS -> new Relaxation(radiusKm + RADIUS_STEP_KM, budget, Optional.empty());
      default -> new Relaxation(radiusKm, budget, Optional.of(step));
    };
  }
}
