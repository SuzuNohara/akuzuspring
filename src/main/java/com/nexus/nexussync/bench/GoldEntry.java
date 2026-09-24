package com.nexus.nexussync.bench;

import java.util.Objects;
import java.util.Set;

/**
 * Gold labels of one couple for the calibration (Fase C1, file {@code
 * calibration/gold/<slug>.yml}).
 *
 * @param coupleId couple identifier, {@code min(a,b) + "-" + max(a,b)}, as in {@link RunRecord}
 * @param expectedTypes {@code activity_type}s a good final list should include (at least one)
 * @param forbiddenTypes {@code activity_type}s that must never appear in the final list
 * @param forbiddenIds activity ids that must never appear in the final list
 * @param notes free text of the labeller
 */
public record GoldEntry(
    String coupleId,
    Set<String> expectedTypes,
    Set<String> forbiddenTypes,
    Set<String> forbiddenIds,
    String notes) {

  /**
   * Validates the components and copies the sets so the record is immutable.
   *
   * @throws IllegalArgumentException if {@code expectedTypes} is empty
   * @implNote O(t + i) time and space, t types, i ids.
   */
  public GoldEntry {
    Objects.requireNonNull(coupleId, "coupleId");
    Objects.requireNonNull(notes, "notes");
    expectedTypes = Set.copyOf(expectedTypes);
    forbiddenTypes = Set.copyOf(forbiddenTypes);
    forbiddenIds = Set.copyOf(forbiddenIds);
    if (expectedTypes.isEmpty()) {
      throw new IllegalArgumentException("expected_types must not be empty for " + coupleId);
    }
  }
}
