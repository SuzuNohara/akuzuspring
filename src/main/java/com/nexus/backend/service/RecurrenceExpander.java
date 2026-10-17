package com.nexus.backend.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * CU28 - Ocurrencias de una regla RRULE (RFC 5545, subconjunto que generan los calendarios del
 * telefono): FREQ DAILY/WEEKLY/MONTHLY/YEARLY, INTERVAL, BYDAY, UNTIL y COUNT. Las fechas se
 * calculan en hora local para que "lunes a las 9" siga siendo a las 9 aunque cambie el offset.
 */
public final class RecurrenceExpander {

    /** Tope de seguridad ante reglas mal formadas. */
    private static final int MAX_ITERATIONS = 5_000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private RecurrenceExpander() {}

    /**
     * Inicios de las ocurrencias que caen en {@code [rangeStart, rangeEnd]}.
     *
     * @param untilColumn UNTIL ya guardado en su columna; si es {@code null} se lee de la regla
     * @param countColumn COUNT ya guardado; si es {@code null} se lee de la regla
     * @implNote O(n) en el numero de ocurrencias desde {@code dtstart} hasta el fin del rango.
     */
    public static List<Instant> occurrences(Instant dtstart, String rrule, Instant untilColumn, Integer countColumn,
                                            Instant rangeStart, Instant rangeEnd, ZoneId zone) {
        String freq = param(rrule, "FREQ");
        int interval = Math.max(1, intParam(rrule, "INTERVAL", 1));
        Integer count = countColumn != null ? countColumn : nullableInt(param(rrule, "COUNT"));
        Instant until = untilColumn != null ? untilColumn : parseUntil(param(rrule, "UNTIL"), zone);
        Set<DayOfWeek> byDay = parseByDay(param(rrule, "BYDAY"));

        LocalDateTime first = LocalDateTime.ofInstant(dtstart, zone);
        List<Instant> result = new ArrayList<>();
        int emitted = 0;

        for (LocalDateTime candidate : candidates(first, freq, interval, byDay)) {
            Instant start = candidate.atZone(zone).toInstant();
            if (start.isAfter(rangeEnd) || (until != null && start.isAfter(until))) break;
            if (count != null && emitted >= count) break;
            emitted++;
            if (!start.isBefore(rangeStart)) result.add(start);
        }
        return result;
    }

    /** Candidatos en orden, sin limite propio: el llamador corta por rango, UNTIL o COUNT. */
    private static Iterable<LocalDateTime> candidates(LocalDateTime first, String freq, int interval,
                                                      Set<DayOfWeek> byDay) {
        List<LocalDateTime> out = new ArrayList<>();
        if ("WEEKLY".equals(freq) && !byDay.isEmpty()) {
            LocalDate weekStart = first.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            List<DayOfWeek> days = byDay.stream().sorted(Comparator.naturalOrder()).toList();
            for (int week = 0; out.size() < MAX_ITERATIONS && week < MAX_ITERATIONS; week += interval) {
                for (DayOfWeek day : days) {
                    LocalDateTime candidate = weekStart.plusWeeks(week).with(TemporalAdjusters.nextOrSame(day))
                            .atTime(first.toLocalTime());
                    if (!candidate.isBefore(first)) out.add(candidate);
                }
            }
            return out;
        }
        LocalDateTime current = first;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (byDay.isEmpty() || byDay.contains(current.getDayOfWeek())) out.add(current);
            current = switch (freq == null ? "DAILY" : freq) {
                case "WEEKLY" -> current.plusWeeks(interval);
                case "MONTHLY" -> current.plusMonths(interval);
                case "YEARLY" -> current.plusYears(interval);
                default -> current.plusDays(interval);
            };
        }
        return out;
    }

    /** UNTIL como fecha (incluye todo ese dia local), fecha-hora UTC ("Z") o fecha-hora local. */
    static Instant parseUntil(String value, ZoneId zone) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.length() == 8) {
                return LocalDate.parse(value, DATE).plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1);
            }
            if (value.endsWith("Z")) {
                return LocalDateTime.parse(value.substring(0, value.length() - 1), DATE_TIME).toInstant(ZoneOffset.UTC);
            }
            return LocalDateTime.parse(value, DATE_TIME).atZone(zone).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** BYDAY=MO,TU o con posicion (1MO, -1FR): la posicion se ignora en este subconjunto. */
    private static Set<DayOfWeek> parseByDay(String value) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (value == null) return days;
        for (String token : value.split(",")) {
            String code = token.replaceAll("[^A-Za-z]", "").toUpperCase();
            switch (code) {
                case "MO" -> days.add(DayOfWeek.MONDAY);
                case "TU" -> days.add(DayOfWeek.TUESDAY);
                case "WE" -> days.add(DayOfWeek.WEDNESDAY);
                case "TH" -> days.add(DayOfWeek.THURSDAY);
                case "FR" -> days.add(DayOfWeek.FRIDAY);
                case "SA" -> days.add(DayOfWeek.SATURDAY);
                case "SU" -> days.add(DayOfWeek.SUNDAY);
                default -> { }
            }
        }
        return days;
    }

    private static String param(String rrule, String name) {
        if (rrule == null) return null;
        for (String part : rrule.replaceFirst("^RRULE:", "").split(";")) {
            if (part.startsWith(name + "=")) return part.substring(name.length() + 1);
        }
        return null;
    }

    private static int intParam(String rrule, String name, int fallback) {
        Integer value = nullableInt(param(rrule, name));
        return value != null ? value : fallback;
    }

    private static Integer nullableInt(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
