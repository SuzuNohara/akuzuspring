package com.nexus.nexussync.places;

import java.util.OptionalDouble;

/**
 * One candidate place for an activity (§3.9).
 *
 * @param placeId slug of the place
 * @param distanceAkm distance from person A, empty when A has no location
 * @param distanceBkm distance from person B, empty when B has no location
 * @param hoursUnknown whether the opening hours are missing or outside the parsed subset
 * @param verified whether the place was verified by a human
 */
public record PlaceOption(
    String placeId,
    OptionalDouble distanceAkm,
    OptionalDouble distanceBkm,
    boolean hoursUnknown,
    boolean verified) {}
