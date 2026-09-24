package com.nexus.nexussync.bench;

/**
 * How {@link Selector} compares the experiments of a sweep.
 *
 * <p>{@link #MEAN} compares the point metrics (the means over runs), as the Fase C3 rule states.
 * {@link #CI_LOWER} (compare by the lower bound of a confidence interval, proposed in chapters 8
 * and 9) is reserved: it awaits the developer's approval and {@link Selector} rejects it with
 * {@link UnsupportedOperationException}.
 */
public enum SelectorMode {
  /** Compare the metric means. */
  MEAN,
  /** Compare lower confidence bounds; not implemented (pending approval). */
  CI_LOWER
}
