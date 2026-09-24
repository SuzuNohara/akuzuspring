package com.nexus.nexussync.context;

import com.nexus.nexussync.catalog.Daypart;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Time window shared by both persons of the couple.
 *
 * @param day day of the week
 * @param start start of the shared window
 * @param end end of the shared window
 * @param daypart part of the day of {@code start}: before 12 MORNING, before 19 AFTERNOON, else
 *     NIGHT
 */
public record Window(DayOfWeek day, LocalTime start, LocalTime end, Daypart daypart) {}
