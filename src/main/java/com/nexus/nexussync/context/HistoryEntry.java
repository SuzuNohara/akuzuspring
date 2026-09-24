package com.nexus.nexussync.context;

import java.time.LocalDate;
import java.util.OptionalInt;

/**
 * One past offer of an activity to the couple, as seen by one person.
 *
 * @param activityId slug of the activity
 * @param date day of the offer
 * @param offered whether the activity was offered
 * @param chosen whether the couple chose it
 * @param rating rating from 1 to 5 given by this person, when there is one
 */
public record HistoryEntry(
    String activityId, LocalDate date, boolean offered, boolean chosen, OptionalInt rating) {}
