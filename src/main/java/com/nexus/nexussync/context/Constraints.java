package com.nexus.nexussync.context;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Hard constraints of a person: budget, travel range and one free window per week.
 *
 * <p>The window never crosses midnight: {@code windowEnd} is strictly after {@code windowStart}
 * ({@link ProfileLoader} rejects anything else).
 *
 * @param budgetBand one of FREE, LOW, MID, HIGH, PREMIUM
 * @param travelBand one of NEAR, MID, FAR
 * @param windowDay day of the free window
 * @param windowStart start of the free window
 * @param windowEnd end of the free window
 */
public record Constraints(
    String budgetBand,
    String travelBand,
    DayOfWeek windowDay,
    LocalTime windowStart,
    LocalTime windowEnd) {}
