package com.nexus.nexussync.sampler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.Year;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Season table of the pipeline (T-22, D-06). */
final class SeasonsTest {

  // U4-16
  @Test
  void given_firstOfNovember_when_active_then_onlyDiaDeMuertos() {
    assertThat(Seasons.active(LocalDate.of(2026, 11, 1))).containsExactly(Seasons.DIA_DE_MUERTOS);
  }

  // U4-16
  @Test
  void given_valentines_when_active_then_sanValentinAndWinter() {
    assertThat(Seasons.active(LocalDate.of(2026, 2, 14)))
        .containsExactlyInAnyOrder(Seasons.SAN_VALENTIN, Seasons.WINTER);
  }

  // U4-16
  @Test
  void given_christmas_when_active_then_navidadWinterAndVacations() {
    assertThat(Seasons.active(LocalDate.of(2026, 12, 25)))
        .containsExactlyInAnyOrder(Seasons.NAVIDAD, Seasons.WINTER, Seasons.VACATIONS);
  }

  // U4-16
  @Test
  void given_midJuly_when_active_then_rainySummerAndVacations() {
    assertThat(Seasons.active(LocalDate.of(2026, 7, 15)))
        .containsExactlyInAnyOrder(Seasons.RAINY, Seasons.SUMMER, Seasons.VACATIONS);
  }

  @Test
  void given_holyWeek2026_when_active_then_semanaSantaAndVacationsWithinBounds() {
    assertThat(Seasons.active(LocalDate.of(2026, 4, 1)))
        .containsExactlyInAnyOrder(Seasons.PRIMAVERA, Seasons.SEMANA_SANTA, Seasons.VACATIONS);
    assertThat(Seasons.active(LocalDate.of(2026, 3, 29))).contains(Seasons.SEMANA_SANTA);
    assertThat(Seasons.active(LocalDate.of(2026, 4, 5))).contains(Seasons.SEMANA_SANTA);
    assertThat(Seasons.active(LocalDate.of(2026, 3, 28))).containsExactly(Seasons.PRIMAVERA);
    assertThat(Seasons.active(LocalDate.of(2026, 4, 6))).containsExactly(Seasons.PRIMAVERA);
  }

  @Test
  void given_everyTabulatedYear_when_easterSunday_then_semanaSanta() {
    assertThat(Seasons.inHolyWeek(LocalDate.of(2027, 3, 28))).isTrue();
    assertThat(Seasons.inHolyWeek(LocalDate.of(2028, 4, 16))).isTrue();
    assertThat(Seasons.inHolyWeek(LocalDate.of(2029, 4, 1))).isTrue();
    assertThat(Seasons.inHolyWeek(LocalDate.of(2030, 4, 21))).isTrue();
    assertThat(Seasons.inHolyWeek(LocalDate.of(2030, 4, 22))).isFalse();
    assertThat(Seasons.inHolyWeek(LocalDate.of(2027, 3, 20))).isFalse();
  }

  @Test
  void given_yearOutsideTable_when_active_then_noSemanaSanta() {
    assertThat(Seasons.active(LocalDate.of(2025, 4, 16))).containsExactly(Seasons.PRIMAVERA);
    assertThat(Seasons.active(LocalDate.of(2031, 4, 13))).containsExactly(Seasons.PRIMAVERA);
  }

  @Test
  void given_spansWrappingTheYear_when_activeOnBounds_then_inclusive() {
    assertThat(Seasons.active(LocalDate.of(2026, 1, 6)))
        .containsExactlyInAnyOrder(Seasons.WINTER, Seasons.VACATIONS, Seasons.NAVIDAD);
    assertThat(Seasons.active(LocalDate.of(2026, 1, 7))).containsExactly(Seasons.WINTER);
    assertThat(Seasons.active(LocalDate.of(2026, 12, 20)))
        .containsExactlyInAnyOrder(Seasons.VACATIONS, Seasons.NAVIDAD);
    assertThat(Seasons.active(LocalDate.of(2026, 12, 21)))
        .containsExactlyInAnyOrder(Seasons.WINTER, Seasons.VACATIONS, Seasons.NAVIDAD);
    assertThat(Seasons.active(LocalDate.of(2026, 12, 1))).containsExactly(Seasons.NAVIDAD);
    assertThat(Seasons.active(LocalDate.of(2026, 11, 30))).isEmpty();
  }

  @Test
  void given_plainSpanBounds_when_active_then_inclusive() {
    assertThat(Seasons.active(LocalDate.of(2026, 3, 20))).containsExactly(Seasons.WINTER);
    assertThat(Seasons.active(LocalDate.of(2026, 3, 21))).containsExactly(Seasons.PRIMAVERA);
    assertThat(Seasons.active(LocalDate.of(2026, 6, 1)))
        .containsExactlyInAnyOrder(Seasons.PRIMAVERA, Seasons.RAINY);
    assertThat(Seasons.active(LocalDate.of(2026, 9, 22)))
        .containsExactlyInAnyOrder(Seasons.SUMMER, Seasons.RAINY);
    assertThat(Seasons.active(LocalDate.of(2026, 9, 23))).containsExactly(Seasons.RAINY);
    assertThat(Seasons.active(LocalDate.of(2026, 10, 15))).containsExactly(Seasons.RAINY);
    assertThat(Seasons.active(LocalDate.of(2026, 10, 16))).isEmpty();
    assertThat(Seasons.active(LocalDate.of(2026, 8, 15)))
        .containsExactlyInAnyOrder(Seasons.SUMMER, Seasons.RAINY, Seasons.VACATIONS);
    assertThat(Seasons.active(LocalDate.of(2026, 8, 16)))
        .containsExactlyInAnyOrder(Seasons.SUMMER, Seasons.RAINY);
    assertThat(Seasons.active(LocalDate.of(2026, 2, 6))).containsExactly(Seasons.WINTER);
  }

  @Test
  void given_everyDayOf2026And2028_when_active_then_neverAnyAndUnmodifiable() {
    for (int year : new int[] {2026, 2028}) {
      LocalDate day = LocalDate.of(year, 1, 1);
      for (int i = 0; i < Year.of(year).length(); i++) {
        Set<String> active = Seasons.active(day);
        assertThat(active).as("%s", day).doesNotContain(Seasons.ANY);
        assertThatThrownBy(() -> active.add(Seasons.ANY))
            .isInstanceOf(UnsupportedOperationException.class);
        day = day.plusDays(1);
      }
    }
  }

  @Test
  void given_nullDate_when_active_then_nullPointer() {
    assertThatThrownBy(() -> Seasons.active(null)).isInstanceOf(NullPointerException.class);
  }
}
