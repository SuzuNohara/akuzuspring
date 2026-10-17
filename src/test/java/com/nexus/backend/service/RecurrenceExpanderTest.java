package com.nexus.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU28 - Ocurrencias de eventos recurrentes del calendario del telefono. Casos tomados de un
 * calendario real: "Trabajo" de lunes a viernes salia solo los lunes porque se ignoraba BYDAY.
 */
class RecurrenceExpanderTest {

    private static final ZoneId CDMX = ZoneId.of("America/Mexico_City");

    // Viernes 16-oct-2026 00:00 CDMX al jueves 29-oct-2026 23:59 CDMX
    private static final Instant FROM = Instant.parse("2026-10-16T06:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-30T05:59:59Z");

    private static List<LocalDate> days(List<Instant> starts) {
        return starts.stream().map(s -> s.atZone(CDMX).toLocalDate()).toList();
    }

    @Test
    @DisplayName("WEEKLY con BYDAY repite en cada dia indicado, no solo cada 7 dias")
    void weeklyByDay() {
        // "Trabajo": lunes 14-sep-2026 09:00 CDMX, de lunes a viernes
        List<Instant> starts = RecurrenceExpander.occurrences(
                Instant.parse("2026-09-14T15:00:00Z"), "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;UNTIL=20261211",
                null, null, FROM, TO, CDMX);

        assertThat(days(starts)).containsExactly(
                LocalDate.of(2026, 10, 16),
                LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21),
                LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23),
                LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28),
                LocalDate.of(2026, 10, 29));
        assertThat(starts).allSatisfy(s -> assertThat(s.atZone(CDMX).getHour()).isEqualTo(9));
    }

    @Test
    @DisplayName("respeta el UNTIL escrito en la regla aunque la columna venga vacia")
    void untilInsideRule() {
        // "Trabajo terminal": hasta el 20-nov; en el rango del 16 al 29 de octubre todavia aplica
        List<Instant> before = RecurrenceExpander.occurrences(
                Instant.parse("2026-09-14T21:00:00Z"), "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;UNTIL=20261020",
                null, null, FROM, TO, CDMX);

        assertThat(days(before)).containsExactly(
                LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20));
    }

    @Test
    @DisplayName("INTERVAL salta semanas completas")
    void weeklyInterval() {
        List<Instant> starts = RecurrenceExpander.occurrences(
                Instant.parse("2026-10-05T15:00:00Z"), "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE",
                null, null, FROM, TO, CDMX);

        assertThat(days(starts)).containsExactly(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 21));
    }

    @Test
    @DisplayName("COUNT cuenta ocurrencias, no semanas")
    void countOccurrences() {
        // Lunes 19-oct: 3 ocurrencias = lunes, martes y miercoles
        List<Instant> starts = RecurrenceExpander.occurrences(
                Instant.parse("2026-10-19T15:00:00Z"), "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;COUNT=3",
                null, 3, FROM, TO, CDMX);

        assertThat(days(starts)).containsExactly(
                LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21));
    }

    @Test
    @DisplayName("DAILY y MONTHLY siguen funcionando")
    void dailyAndMonthly() {
        List<Instant> daily = RecurrenceExpander.occurrences(
                Instant.parse("2026-09-12T05:00:00Z"), "FREQ=DAILY", null, null, FROM, TO, CDMX);
        List<Instant> monthly = RecurrenceExpander.occurrences(
                Instant.parse("2026-09-19T18:00:00Z"), "FREQ=MONTHLY", null, null, FROM, TO, CDMX);

        assertThat(daily).hasSize(14);
        assertThat(days(monthly)).containsExactly(LocalDate.of(2026, 10, 19));
    }

    @Test
    @DisplayName("la columna UNTIL manda si viene llena")
    void untilColumn() {
        List<Instant> starts = RecurrenceExpander.occurrences(
                Instant.parse("2026-09-12T05:00:00Z"), "FREQ=DAILY",
                Instant.parse("2026-10-18T06:00:00Z"), null, FROM, TO, CDMX);

        assertThat(days(starts)).containsExactly(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 17));
    }
}
