package com.nexus.nexussync.context;

import com.nexus.nexussync.params.ContextParams;
import com.nexus.nexussync.params.Params;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the context of a couple on a given day (unit U3). */
public final class ContextBuilder {

  private ContextBuilder() {}

  /**
   * Builds the context: climates, shared windows, weather per window and rated dates.
   *
   * @param a first person
   * @param b second person
   * @param today reference day
   * @param p parameters; only {@code context} is read
   * @param weather forecast per window index; a missing index becomes {@code UNKNOWN} and indexes
   *     without a window are dropped
   * @return the immutable context
   * @implNote O(e + h) time and space, e emotional entries and h history entries of both persons.
   */
  public static Context build(
      Profile a, Profile b, LocalDate today, Params p, Map<Integer, Weather> weather) {
    ContextParams cp = p.context();
    List<Window> windows = Windows.overlap(a.constraints(), b.constraints());
    return new Context(
        coupleId(a.userId(), b.userId()),
        a,
        b,
        ClimateCalculator.of(a.emotionalRecent(), today, cp.emotionWindowDays()),
        ClimateCalculator.of(b.emotionalRecent(), today, cp.emotionWindowDays()),
        windows,
        weatherByWindow(windows.size(), weather),
        ratedDates(a, b, today, cp.historyWindowDays()),
        today);
  }

  /**
   * Identifier of a couple, independent of the order of its members.
   *
   * @param x id of one person
   * @param y id of the other person
   * @return {@code min + "-" + max}
   * @implNote O(1) time and space.
   */
  static String coupleId(int x, int y) {
    return Math.min(x, y) + "-" + Math.max(x, y);
  }

  /**
   * Distinct {@code (activity_id, date)} pairs chosen and rated by either person inside the window.
   *
   * @param a first person
   * @param b second person
   * @param today reference day
   * @param days days before {@code today} that are considered
   * @return number of distinct rated dates
   * @implNote O(h) time and space in the number of history entries of both persons.
   */
  static int ratedDates(Profile a, Profile b, LocalDate today, int days) {
    Set<String> keys = new HashSet<>();
    addRated(a.history(), today, days, keys);
    addRated(b.history(), today, days, keys);
    return keys.size();
  }

  private static void addRated(
      List<HistoryEntry> history, LocalDate today, int days, Set<String> keys) {
    for (HistoryEntry h : history) {
      if (h.chosen()
          && h.rating().isPresent()
          && ClimateCalculator.withinDays(h.date(), today, days)) {
        keys.add(h.activityId() + "|" + h.date());
      }
    }
  }

  private static Map<Integer, Weather> weatherByWindow(int windows, Map<Integer, Weather> weather) {
    Map<Integer, Weather> out = new HashMap<>();
    for (int i = 0; i < windows; i++) {
      out.put(i, weather.getOrDefault(i, Weather.UNKNOWN));
    }
    return out;
  }
}
