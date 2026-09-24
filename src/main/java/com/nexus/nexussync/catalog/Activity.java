package com.nexus.nexussync.catalog;

import java.util.Set;

/**
 * One row of {@code activities.csv}, restricted to the columns the sampler and the gate use (§3.2).
 *
 * <p>Set components are copied defensively and exposed unmodifiable (deviation D-08).
 *
 * @param activityId slug of the activity (CSV column {@code activity_id})
 * @param title short title
 * @param activityType value of the {@code activity_type} enum of kb/schema.md
 * @param locationScope {@code HOME} or {@code CITY}
 * @param placeTypes place types where it can happen; empty for {@code HOME}
 * @param interests slugs of {@code kb/interests.yml}
 * @param dayparts parts of the day in which it fits
 * @param seasons values of the {@code seasons} enum; {@code ANY} when it fits all year
 * @param durationMin minimum duration in minutes
 * @param durationAvg typical duration in minutes
 * @param durationMax maximum duration in minutes
 * @param difficultyPhysical physical difficulty, 0 to 10
 * @param difficultyMental mental difficulty, 0 to 10
 * @param collaboration how much both members must take part, 0 to 10
 * @param preparation preparation required, 0 to 10
 * @param costMxnPp cost per person in MXN
 * @param priceBand band derived from the cost ({@code FREE} to {@code PREMIUM})
 * @param outdoor whether it happens outdoors ({@code is_outdoor == 1})
 * @param weatherOk weather values under which it works
 * @param ambience ambience values of the activity
 * @param description short description shown to the agents
 */
public record Activity(
    String activityId,
    String title,
    String activityType,
    LocationScope locationScope,
    Set<String> placeTypes,
    Set<String> interests,
    Set<Daypart> dayparts,
    Set<String> seasons,
    int durationMin,
    int durationAvg,
    int durationMax,
    int difficultyPhysical,
    int difficultyMental,
    int collaboration,
    int preparation,
    int costMxnPp,
    String priceBand,
    boolean outdoor,
    Set<String> weatherOk,
    Set<String> ambience,
    String description) {

  /**
   * Copies every set component so the record never shares state with its caller.
   *
   * @implNote O(k) time and space, k = total number of set elements.
   */
  public Activity {
    placeTypes = Set.copyOf(placeTypes);
    interests = Set.copyOf(interests);
    dayparts = Set.copyOf(dayparts);
    seasons = Set.copyOf(seasons);
    weatherOk = Set.copyOf(weatherOk);
    ambience = Set.copyOf(ambience);
  }
}
