package com.nexus.nexussync.context;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Context of a couple on a given day: the input of the sampler and of the gate (unit U3).
 *
 * @param coupleId {@code min(idA, idB) + "-" + max(idA, idB)}
 * @param a first person
 * @param b second person
 * @param climateA emotional climate of {@code a}
 * @param climateB emotional climate of {@code b}
 * @param windows shared windows of at least 60 minutes
 * @param weather forecast per index of {@code windows}; every index is present
 * @param ratedDatesCount distinct chosen and rated dates inside the history window
 * @param today reference day
 */
public record Context(
    String coupleId,
    Profile a,
    Profile b,
    Climate climateA,
    Climate climateB,
    List<Window> windows,
    Map<Integer, Weather> weather,
    int ratedDatesCount,
    LocalDate today) {

  /**
   * Copies the collections so the record is immutable.
   *
   * @implNote O(n) time and space in the number of windows.
   */
  public Context {
    windows = List.copyOf(windows);
    weather = Map.copyOf(weather);
  }
}
