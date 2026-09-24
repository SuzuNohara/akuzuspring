package com.nexus.nexussync.ann;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maximum number of agent calls allowed within one run (A2).
 *
 * <p>Shared by the gate and the couple runner: every agent call, retries included, must first
 * obtain a unit with {@link #tryConsume()}. Once exhausted, callers degrade instead of failing.
 */
public final class CallBudget {

  private final int maxCalls;
  private final AtomicInteger used = new AtomicInteger();

  /**
   * Creates a budget of {@code maxCalls} calls, none used.
   *
   * @param maxCalls maximum number of calls, {@code >= 0}
   * @throws IllegalArgumentException if {@code maxCalls} is negative
   * @implNote O(1) time and space.
   */
  public CallBudget(int maxCalls) {
    if (maxCalls < 0) {
      throw new IllegalArgumentException("maxCalls must be >= 0: " + maxCalls);
    }
    this.maxCalls = maxCalls;
  }

  /**
   * Consumes one call if the budget is not exhausted.
   *
   * @return {@code true} if a call was consumed, {@code false} if the budget was already exhausted
   * @implNote O(1) time and space; thread-safe without locks.
   */
  public boolean tryConsume() {
    int current = used.get();
    while (current < maxCalls) {
      if (used.compareAndSet(current, current + 1)) {
        return true;
      }
      current = used.get();
    }
    return false;
  }

  /**
   * Returns how many calls have been consumed so far.
   *
   * @return the number of successful {@link #tryConsume()} calls, never above the maximum
   * @implNote O(1) time and space.
   */
  public int used() {
    return used.get();
  }
}
