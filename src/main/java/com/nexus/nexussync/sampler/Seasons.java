package com.nexus.nexussync.sampler;

import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Seasons of the pipeline active on a given date in CDMX (unit U4, decision D-06).
 *
 * <p>Fixed table, inclusive on both ends; a span that crosses the new year wraps:
 *
 * <table>
 *   <caption>Season table (D-06)</caption>
 *   <tr><th>Season</th><th>Dates</th></tr>
 *   <tr><td>{@value #WINTER}</td><td>21 Dec - 20 Mar</td></tr>
 *   <tr><td>{@value #PRIMAVERA}</td><td>21 Mar - 20 Jun</td></tr>
 *   <tr><td>{@value #SUMMER}</td><td>21 Jun - 22 Sep</td></tr>
 *   <tr><td>{@value #RAINY}</td><td>1 Jun - 15 Oct</td></tr>
 *   <tr><td>{@value #VACATIONS}</td><td>1 Jul - 15 Aug, 20 Dec - 6 Jan and the Holy Week of
 *       the year</td></tr>
 *   <tr><td>{@value #DIA_DE_MUERTOS}</td><td>25 Oct - 3 Nov</td></tr>
 *   <tr><td>{@value #NAVIDAD}</td><td>1 Dec - 6 Jan</td></tr>
 *   <tr><td>{@value #SAN_VALENTIN}</td><td>7 Feb - 14 Feb</td></tr>
 *   <tr><td>{@value #SEMANA_SANTA}</td><td>Palm Sunday - Easter Sunday, by table: 2026 29 Mar -
 *       5 Apr, 2027 21 Mar - 28 Mar, 2028 9 Apr - 16 Apr, 2029 25 Mar - 1 Apr, 2030 14 Apr -
 *       21 Apr; years outside the table have no Holy Week</td></tr>
 * </table>
 *
 * <p>{@value #ANY} is the catalog wildcard ("valid in any season") and is never returned by {@link
 * #active(LocalDate)}. Autumn has no season of its own, so a date such as 20 Oct yields an empty
 * set.
 */
public final class Seasons {

  /** Catalog wildcard: the activity fits any season. Never active by itself. */
  public static final String ANY = "ANY";

  /** 21 Dec - 20 Mar. */
  public static final String WINTER = "WINTER";

  /** 21 Mar - 20 Jun. */
  public static final String PRIMAVERA = "PRIMAVERA";

  /** 21 Jun - 22 Sep. */
  public static final String SUMMER = "SUMMER";

  /** 1 Jun - 15 Oct. */
  public static final String RAINY = "RAINY";

  /** 1 Jul - 15 Aug, 20 Dec - 6 Jan and the Holy Week of the year. */
  public static final String VACATIONS = "VACATIONS";

  /** 25 Oct - 3 Nov. */
  public static final String DIA_DE_MUERTOS = "DIA_DE_MUERTOS";

  /** 1 Dec - 6 Jan. */
  public static final String NAVIDAD = "NAVIDAD";

  /** 7 Feb - 14 Feb. */
  public static final String SAN_VALENTIN = "SAN_VALENTIN";

  /** Palm Sunday to Easter Sunday, by table (2026-2030). */
  public static final String SEMANA_SANTA = "SEMANA_SANTA";

  /** Days from Palm Sunday to Easter Sunday, both inclusive. */
  private static final int HOLY_WEEK_SPAN_DAYS = 7;

  private static final List<Span> SPANS =
      List.of(
          span(WINTER, Month.DECEMBER, 21, Month.MARCH, 20),
          span(PRIMAVERA, Month.MARCH, 21, Month.JUNE, 20),
          span(SUMMER, Month.JUNE, 21, Month.SEPTEMBER, 22),
          span(RAINY, Month.JUNE, 1, Month.OCTOBER, 15),
          span(VACATIONS, Month.JULY, 1, Month.AUGUST, 15),
          span(VACATIONS, Month.DECEMBER, 20, Month.JANUARY, 6),
          span(DIA_DE_MUERTOS, Month.OCTOBER, 25, Month.NOVEMBER, 3),
          span(NAVIDAD, Month.DECEMBER, 1, Month.JANUARY, 6),
          span(SAN_VALENTIN, Month.FEBRUARY, 7, Month.FEBRUARY, 14));

  /** Palm Sunday per year; Easter Sunday is seven days later. */
  private static final Map<Integer, LocalDate> PALM_SUNDAY =
      Map.of(
          2026, LocalDate.of(2026, Month.MARCH, 29),
          2027, LocalDate.of(2027, Month.MARCH, 21),
          2028, LocalDate.of(2028, Month.APRIL, 9),
          2029, LocalDate.of(2029, Month.MARCH, 25),
          2030, LocalDate.of(2030, Month.APRIL, 14));

  private Seasons() {}

  /**
   * Seasons active on {@code date}, in table order, never containing {@value #ANY}.
   *
   * @param date the date to classify
   * @return an unmodifiable set, possibly empty
   * @implNote O(s) time, s = spans of the table (constant); O(s) space for the result.
   */
  public static Set<String> active(LocalDate date) {
    Objects.requireNonNull(date, "date");
    MonthDay day = MonthDay.from(date);
    Set<String> out = new LinkedHashSet<>();
    for (Span span : SPANS) {
      if (span.covers(day)) {
        out.add(span.season());
      }
    }
    if (inHolyWeek(date)) {
      out.add(SEMANA_SANTA);
      out.add(VACATIONS);
    }
    return Collections.unmodifiableSet(out);
  }

  /**
   * Tells whether {@code date} falls between Palm Sunday and Easter Sunday of its year, inclusive.
   *
   * @param date the date to test
   * @return {@code false} for every year outside the table
   * @implNote O(1) time and space.
   */
  static boolean inHolyWeek(LocalDate date) {
    LocalDate palm = PALM_SUNDAY.get(date.getYear());
    return palm != null
        && !date.isBefore(palm)
        && !date.isAfter(palm.plusDays(HOLY_WEEK_SPAN_DAYS));
  }

  private static Span span(String season, Month fromMonth, int fromDay, Month toMonth, int toDay) {
    return new Span(season, MonthDay.of(fromMonth, fromDay), MonthDay.of(toMonth, toDay));
  }

  /** Inclusive span of month-days; {@code start > end} means it wraps over the new year. */
  private record Span(String season, MonthDay start, MonthDay end) {

    boolean covers(MonthDay day) {
      boolean afterStart = !day.isBefore(start);
      boolean beforeEnd = !day.isAfter(end);
      return start.isAfter(end) ? afterStart || beforeEnd : afterStart && beforeEnd;
    }
  }
}
