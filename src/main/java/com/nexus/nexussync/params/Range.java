package com.nexus.nexussync.params;

import java.math.BigDecimal;

/**
 * Closed numeric interval {@code [min, max]} accepted by a parameter.
 *
 * @param min lower bound, inclusive
 * @param max upper bound, inclusive
 */
public record Range(double min, double max) {

  /**
   * Rejects inverted bounds.
   *
   * @throws IllegalArgumentException if {@code min > max}
   * @implNote O(1) time and space.
   */
  public Range {
    if (min > max) {
      throw new IllegalArgumentException("rango invertido: " + min + " > " + max);
    }
  }

  /**
   * Tells whether {@code value} lies inside the interval; {@code NaN} never does.
   *
   * @param value the value to test
   * @return {@code true} if {@code min <= value <= max}
   * @implNote O(1) time and space.
   */
  public boolean contains(double value) {
    return value >= min && value <= max;
  }

  /**
   * Renders the interval as {@code [min, max]} without trailing zeros, e.g. {@code [0, 2]}.
   *
   * @return the compact representation used in validation messages
   * @implNote O(1) time and space.
   */
  @Override
  public String toString() {
    return "[" + compact(min) + ", " + compact(max) + "]";
  }

  private static String compact(double d) {
    return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
  }
}
