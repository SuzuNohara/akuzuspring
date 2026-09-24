package com.nexus.nexussync.ann;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maximum number of agent calls allowed within one run (A2).
 *
 * <p>Shared by the gate and the couple runner: every agent call, retries included, must first
 * obtain a unit with {@link #tryConsume()}, or reserve the units of a whole round at once with
 * {@link #tryConsume(int)} (D-28). Once exhausted, callers degrade instead of failing. Because a
 * reservation is all-or-nothing and callers dispatch exactly what they reserved, {@link #used()} is
 * exactly the number of real agent dispatches.
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
    return tryConsume(1);
  }

  /**
   * Atomically reserves {@code n} calls (D-28): consumes them only if at least {@code n} remain;
   * otherwise consumes nothing.
   *
   * @param n number of calls to reserve, {@code >= 0}
   * @return {@code true} if the {@code n} calls were consumed, {@code false} if fewer than {@code
   *     n} remained (nothing consumed)
   * @throws IllegalArgumentException if {@code n} is negative
   * @implNote O(1) time and space; thread-safe without locks.
   */
  public boolean tryConsume(int n) {
    if (n < 0) {
      throw new IllegalArgumentException("n must be >= 0: " + n);
    }
    int current = used.get();
    while (current <= maxCalls - n) {
      if (used.compareAndSet(current, current + n)) {
        return true;
      }
      current = used.get();
    }
    return false;
  }

  /**
   * Returns how many calls have been consumed so far.
   *
   * @return the number of units consumed by successful reservations, never above the maximum
   * @implNote O(1) time and space.
   */
  public int used() {
    return used.get();
  }
}
