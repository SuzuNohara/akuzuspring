package com.nexus.nexussync.catalog;

import java.util.OptionalInt;

/**
 * One row of {@code activity_places.csv}: an activity that can happen at a place (§3.2).
 *
 * @param activityId slug of the activity
 * @param placeId slug of the place
 * @param costOverride cost per person fixed by the place, empty when the activity cost applies
 */
public record Link(String activityId, String placeId, OptionalInt costOverride) {}
