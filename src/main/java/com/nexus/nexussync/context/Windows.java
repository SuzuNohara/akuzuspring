package com.nexus.nexussync.context;

import com.nexus.nexussync.catalog.Daypart;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/** Shared windows of a couple and their part of the day (unit U3). */
final class Windows {

  /** Minimum length of a shared window. */
  static final Duration MIN_OVERLAP = Duration.ofMinutes(60);

  private static final int NOON = 12;
  private static final int EVENING = 19;

  private Windows() {}

  /**
   * Overlap of the single weekly window of each person: same day and at least {@link #MIN_OVERLAP}.
   *
   * @param a constraints of the first person
   * @param b constraints of the second person
   * @return zero or one shared window
   * @implNote O(1) time and space.
   */
  static List<Window> overlap(Constraints a, Constraints b) {
    if (a.windowDay() != b.windowDay()) {
      return List.of();
    }
    LocalTime start = a.windowStart().isAfter(b.windowStart()) ? a.windowStart() : b.windowStart();
    LocalTime end = a.windowEnd().isBefore(b.windowEnd()) ? a.windowEnd() : b.windowEnd();
    if (Duration.between(start, end).compareTo(MIN_OVERLAP) < 0) {
      return List.of();
    }
    return List.of(new Window(a.windowDay(), start, end, daypartOf(start)));
  }

  /**
   * Part of the day of a start time: before 12 MORNING, before 19 AFTERNOON, else NIGHT.
   *
   * @param start start time
   * @return the part of the day
   * @implNote O(1) time and space.
   */
  static Daypart daypartOf(LocalTime start) {
    if (start.getHour() < NOON) {
      return Daypart.MORNING;
    }
    if (start.getHour() < EVENING) {
      return Daypart.AFTERNOON;
    }
    return Daypart.NIGHT;
  }
}
