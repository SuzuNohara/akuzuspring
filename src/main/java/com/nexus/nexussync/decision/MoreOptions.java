package com.nexus.nexussync.decision;

import com.nexus.nexussync.params.DecisionParams;
import java.util.List;

/**
 * "More options" paging over the remaining activities of the gate (unit U8).
 *
 * <p>Each request serves the next {@code moreOptionsBatch} ids after {@code offset}; an offset at
 * or past the end yields an empty page.
 */
public final class MoreOptions {

  private MoreOptions() {}

  /**
   * Returns the next page of remaining ids.
   *
   * @param remaining remaining ids in gate order, never {@code null}
   * @param offset number of ids already served, {@code >= 0}
   * @param p decision parameters with the batch size, never {@code null}
   * @return an unmodifiable list with at most {@code moreOptionsBatch} ids; empty when {@code
   *     offset >= remaining.size()} or the batch is not positive
   * @throws IllegalArgumentException if {@code offset} is negative
   * @implNote O(b) time and space, b = batch size.
   */
  public static List<String> next(List<String> remaining, int offset, DecisionParams p) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be >= 0: " + offset);
    }
    int batch = p.moreOptionsBatch();
    if (batch <= 0 || offset >= remaining.size()) {
      return List.of();
    }
    int end = offset + Math.min(batch, remaining.size() - offset);
    return List.copyOf(remaining.subList(offset, end));
  }
}
