package com.nexus.nexussync.context;

import java.time.LocalDate;

/**
 * One recent emotional record of a person.
 *
 * <p>{@code code} is kept as text on purpose: an unknown code is not a load error, the {@link
 * ClimateCalculator} ignores it and counts it.
 *
 * @param date day of the record
 * @param code emotion code, normally one of {@link EmotionCode}
 * @param intensity intensity from 1 to 5
 */
public record EmotionEntry(LocalDate date, String code, int intensity) {}
