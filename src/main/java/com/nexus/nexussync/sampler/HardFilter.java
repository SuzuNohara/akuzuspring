package com.nexus.nexussync.sampler;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.params.FilterName;
import com.nexus.nexussync.params.SamplerParams;
import com.nexus.nexussync.sampler.Budget.PriceBand;
import java.util.Optional;

/** One hard filter of G2: decides whether an activity is out of the sample (§3.4). */
@FunctionalInterface
interface HardFilter {

  /**
   * Tells whether {@code a} is rejected.
   *
   * @param a candidate activity
   * @param ctx context of the couple
   * @param cat catalog, for the linked places
   * @param p sampler parameters
   * @param radiusKm current radius, possibly relaxed
   * @param budget current couple budget band, possibly relaxed
   * @return the name of this filter when the activity is rejected, empty when it passes
   */
  Optional<FilterName> reject(
      Activity a, Context ctx, Catalog cat, SamplerParams p, double radiusKm, PriceBand budget);
}
