package com.nexus.nexussync.sampler;

import com.nexus.nexussync.params.FilterName;
import java.util.List;
import java.util.Map;

/**
 * Result of the sampler for one couple and day (unit U4).
 *
 * @param items sampled activities, in selection order
 * @param relaxations hard filters relaxed to reach the minimum size, in application order
 * @param status outcome of the sampling
 * @param rejected number of activities rejected by each hard filter
 */
public record Sample(
    List<SampleItem> items,
    List<FilterName> relaxations,
    SampleStatus status,
    Map<FilterName, Integer> rejected) {

  /**
   * Copies the collections so the record is immutable.
   *
   * @implNote O(n) time and space in the number of items.
   */
  public Sample {
    items = List.copyOf(items);
    relaxations = List.copyOf(relaxations);
    rejected = Map.copyOf(rejected);
  }
}
