package com.nexus.nexussync.sampler;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Closed budget bands of the sampler and their cost ceilings per person in MXN (unit U4, RN-32).
 */
public final class Budget {

  /** Closed budget band, from cheapest to most expensive. */
  public enum PriceBand {
    /** Ceiling 0 MXN. */
    FREE,
    /** Ceiling 200 MXN. */
    LOW,
    /** Ceiling 800 MXN. */
    MID,
    /** Ceiling 3000 MXN. */
    HIGH,
    /** Ceiling 10000 MXN. */
    PREMIUM
  }

  private static final int[] CEILING_MXN = {0, 200, 800, 3000, 10000};

  /**
   * Ceiling of {@code cost_mxn_pp} per band: FREE 0, LOW 200, MID 800, HIGH 3000, PREMIUM 10000.
   */
  static final Map<PriceBand, Integer> CEILING = ceilings();

  private Budget() {}

  /**
   * Next band up; {@link PriceBand#PREMIUM} stays.
   *
   * @param band current band
   * @return the band one step more expensive, or {@code band} when it is already the top one
   * @implNote O(1) time and space.
   */
  static PriceBand up(PriceBand band) {
    PriceBand[] all = PriceBand.values();
    return all[Math.min(band.ordinal() + 1, all.length - 1)];
  }

  /**
   * Ceiling of a band.
   *
   * @param band the band
   * @return maximum cost per person in MXN
   * @implNote O(1) time and space.
   */
  static int ceiling(PriceBand band) {
    return CEILING.get(band);
  }

  /**
   * Parses a band name as written in {@code Constraints.budgetBand}, case-insensitive.
   *
   * @param name band name
   * @return the band
   * @throws IllegalArgumentException when {@code name} is not a band
   * @implNote O(1) time and space.
   */
  static PriceBand parse(String name) {
    return PriceBand.valueOf(name.trim().toUpperCase(Locale.ROOT));
  }

  /**
   * Cheapest of two band names: the couple budget is the lower band of both persons.
   *
   * @param a band of the first person
   * @param b band of the second person
   * @return the lower band
   * @implNote O(1) time and space.
   */
  static PriceBand lowest(String a, String b) {
    PriceBand x = parse(a);
    PriceBand y = parse(b);
    return x.compareTo(y) <= 0 ? x : y;
  }

  private static Map<PriceBand, Integer> ceilings() {
    Map<PriceBand, Integer> m = new EnumMap<>(PriceBand.class);
    for (PriceBand band : PriceBand.values()) {
      m.put(band, CEILING_MXN[band.ordinal()]);
    }
    return Collections.unmodifiableMap(m);
  }
}
