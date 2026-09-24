package com.nexus.nexussync.places;

import com.nexus.nexussync.context.Window;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reduced parser of OSM {@code opening_hours} (§3.9).
 *
 * <p>Supported subset: {@code 24/7}; rules {@code <days> <times>} where {@code <days>} is a comma
 * list of days ({@code Mo}) or day ranges ({@code Mo-Fr}, wrapping allowed) and {@code <times>} a
 * comma list of {@code HH:MM-HH:MM} ranges on the same day (end up to {@code 24:00}); several rules
 * separated by {@code ;}, the last rule that applies to a day wins. A day no rule mentions is
 * closed. Anything else ({@code off}, {@code PH}, months, weeks, overnight ranges…) is not
 * parseable.
 */
public final class OpeningHours {

  private static final String ALWAYS_OPEN = "24/7";
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern TIME_RANGE = Pattern.compile("(\\d{2}):(\\d{2})-(\\d{2}):(\\d{2})");
  private static final int MINUTES_PER_HOUR = 60;
  private static final int MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;
  private static final int RULE_TOKENS = 2;
  private static final Map<String, DayOfWeek> DAYS =
      Map.of(
          "Mo", DayOfWeek.MONDAY,
          "Tu", DayOfWeek.TUESDAY,
          "We", DayOfWeek.WEDNESDAY,
          "Th", DayOfWeek.THURSDAY,
          "Fr", DayOfWeek.FRIDAY,
          "Sa", DayOfWeek.SATURDAY,
          "Su", DayOfWeek.SUNDAY);

  private OpeningHours() {}

  /** One parsed rule: the days it applies to and its opening ranges. */
  private record Rule(Set<DayOfWeek> days, List<Range> ranges) {}

  /** Opening range in minutes since midnight, {@code from < to <= 1440}. */
  private record Range(int from, int to) {}

  /**
   * Whether the place is open during the whole window.
   *
   * @param osmHours OSM opening hours expression
   * @param w window to cover
   * @return {@code true} if one opening range of the window's day contains the window, {@code
   *     false} if not (or the day is closed), empty if the expression is outside the subset
   * @implNote O(n) time and space, n = length of {@code osmHours}.
   */
  public static Optional<Boolean> covers(String osmHours, Window w) {
    String text = osmHours.strip();
    if (ALWAYS_OPEN.equals(text)) {
      return Optional.of(true);
    }
    List<Range> current = List.of();
    for (String raw : text.split(";", -1)) {
      Optional<Rule> rule = parseRule(raw.strip());
      if (rule.isEmpty()) {
        return Optional.empty();
      }
      if (rule.get().days().contains(w.day())) {
        current = rule.get().ranges();
      }
    }
    return Optional.of(contains(current, w));
  }

  private static boolean contains(List<Range> ranges, Window w) {
    int start = minutes(w.start());
    int end = w.end().equals(LocalTime.MIDNIGHT) ? MINUTES_PER_DAY : minutes(w.end());
    return ranges.stream().anyMatch(r -> r.from() <= start && end <= r.to());
  }

  private static int minutes(LocalTime t) {
    return t.getHour() * MINUTES_PER_HOUR + t.getMinute();
  }

  private static Optional<Rule> parseRule(String text) {
    String[] tokens = WHITESPACE.split(text);
    if (tokens.length != RULE_TOKENS) {
      return Optional.empty();
    }
    Optional<Set<DayOfWeek>> days = parseDays(tokens[0]);
    Optional<List<Range>> ranges = parseRanges(tokens[1]);
    if (days.isEmpty() || ranges.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new Rule(days.get(), ranges.get()));
  }

  private static Optional<Set<DayOfWeek>> parseDays(String text) {
    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    for (String part : text.split(",", -1)) {
      String[] bounds = part.split("-", -1);
      if (bounds.length > RULE_TOKENS
          || !DAYS.containsKey(bounds[0])
          || !DAYS.containsKey(bounds[bounds.length - 1])) {
        return Optional.empty();
      }
      addDayRange(days, DAYS.get(bounds[0]), DAYS.get(bounds[bounds.length - 1]));
    }
    return Optional.of(days);
  }

  private static void addDayRange(Set<DayOfWeek> days, DayOfWeek from, DayOfWeek to) {
    DayOfWeek day = from;
    days.add(day);
    while (day != to) {
      day = day.plus(1);
      days.add(day);
    }
  }

  private static Optional<List<Range>> parseRanges(String text) {
    List<Range> ranges = new ArrayList<>();
    for (String part : text.split(",", -1)) {
      Matcher m = TIME_RANGE.matcher(part);
      if (!m.matches()) {
        return Optional.empty();
      }
      int from = toMinutes(m.group(1), m.group(2));
      int to = toMinutes(m.group(3), m.group(4));
      if (from < 0 || to < 0 || from >= to) {
        return Optional.empty();
      }
      ranges.add(new Range(from, to));
    }
    return Optional.of(List.copyOf(ranges));
  }

  private static int toMinutes(String hours, String mins) {
    int h = Integer.parseInt(hours);
    int m = Integer.parseInt(mins);
    int total = h * MINUTES_PER_HOUR + m;
    return m >= MINUTES_PER_HOUR || total > MINUTES_PER_DAY ? -1 : total;
  }
}
