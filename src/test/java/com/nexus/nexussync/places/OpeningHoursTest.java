package com.nexus.nexussync.places;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.catalog.Daypart;
import com.nexus.nexussync.context.Window;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpeningHoursTest {

  private static final Window MONDAY_AFTERNOON = window(DayOfWeek.MONDAY, "14:00", "16:00");
  private static final Window SATURDAY_MORNING = window(DayOfWeek.SATURDAY, "10:00", "12:00");
  private static final Window SUNDAY_NIGHT = window(DayOfWeek.SUNDAY, "22:00", "23:30");

  private static Window window(DayOfWeek day, String start, String end) {
    return new Window(day, LocalTime.parse(start), LocalTime.parse(end), Daypart.AFTERNOON);
  }

  // U9-07
  @Test
  void givenAlwaysOpen_whenCovers_thenTrue() {
    assertThat(OpeningHours.covers("24/7", SUNDAY_NIGHT)).contains(true);
    assertThat(OpeningHours.covers("  24/7 ", MONDAY_AFTERNOON)).contains(true);
  }

  // U9-07
  @Test
  void givenWeekdayRange_whenWindowInside_thenTrue() {
    assertThat(OpeningHours.covers("Mo-Fr 09:00-18:00", MONDAY_AFTERNOON)).contains(true);
  }

  // U9-07
  @Test
  void givenWeekdayRange_whenDayNotListed_thenFalse() {
    assertThat(OpeningHours.covers("Mo-Fr 09:00-18:00", SATURDAY_MORNING)).contains(false);
  }

  // U9-07
  @Test
  void givenRange_whenWindowOverflowsClosing_thenFalse() {
    Window late = window(DayOfWeek.MONDAY, "17:00", "19:00");
    assertThat(OpeningHours.covers("Mo-Fr 09:00-18:00", late)).contains(false);
  }

  // U9-07
  @Test
  void givenCommaListsOfDaysAndTimes_whenCovers_thenAnyRangeOfListedDayCounts() {
    String hours = "Mo,We,Sa 09:00-11:00,13:00-17:00";
    assertThat(OpeningHours.covers(hours, MONDAY_AFTERNOON)).contains(true);
    assertThat(OpeningHours.covers(hours, SATURDAY_MORNING)).contains(false);
    assertThat(OpeningHours.covers(hours, window(DayOfWeek.TUESDAY, "14:00", "15:00")))
        .contains(false);
  }

  // U9-07
  @Test
  void givenSeveralRules_whenBothApply_thenLastRuleWins() {
    String hours = "Mo-Su 09:00-20:00; Sa 10:00-11:00";
    assertThat(OpeningHours.covers(hours, SATURDAY_MORNING)).contains(false);
    assertThat(OpeningHours.covers(hours, MONDAY_AFTERNOON)).contains(true);
    assertThat(OpeningHours.covers("Mo-Fr 09:00-18:00; Sa 10:00-14:00", SATURDAY_MORNING))
        .contains(true);
  }

  // U9-07
  @Test
  void givenWrappingDayRangeAndMidnightEnd_whenCovers_thenTrue() {
    Window untilMidnight = window(DayOfWeek.SUNDAY, "22:00", "00:00");
    assertThat(OpeningHours.covers("Fr-Su 18:00-24:00", untilMidnight)).contains(true);
    assertThat(OpeningHours.covers("Fr-Su 18:00-24:00", SUNDAY_NIGHT)).contains(true);
    assertThat(OpeningHours.covers("Sa-Mo 18:00-24:00", MONDAY_AFTERNOON)).contains(false);
  }

  // U9-07
  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "Mo-Fr 09:00-18:00; Su off",
        "PH off",
        "Jan-Mar Mo-Fr 09:00-18:00",
        "week 01-10 Mo 09:00-12:00",
        "sunrise-sunset",
        "Mo-Fr 22:00-02:00",
        "Mo-Fr 18:00-09:00",
        "Mo-Fr 09:60-18:00",
        "Mo-Fr 09:00-24:30",
        "Mo-Fr 9:00-18:00",
        "Mo-We-Fr 09:00-18:00",
        "Mo- 09:00-18:00",
        "Xx 09:00-18:00",
        "Mo-Fr 09:00-18:00;",
        "Mo-Fr 09:00-12:00,",
        "09:00-18:00"
      })
  void givenExpressionOutsideSubset_whenCovers_thenEmpty(String hours) {
    assertThat(OpeningHours.covers(hours, MONDAY_AFTERNOON)).isEqualTo(Optional.empty());
  }
}
