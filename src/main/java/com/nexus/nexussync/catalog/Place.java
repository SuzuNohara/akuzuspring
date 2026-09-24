package com.nexus.nexussync.catalog;

import java.util.Optional;

/**
 * One row of {@code places.csv}, restricted to the columns the place assigner uses (§3.2).
 *
 * @param placeId slug of the place (CSV column {@code place_id})
 * @param name display name
 * @param placeType value of the {@code place_type} enum of kb/schema.md
 * @param lat latitude in decimal degrees
 * @param lon longitude in decimal degrees
 * @param borough borough name as written in kb/schema.md
 * @param openingHours OSM opening hours, empty when the CSV column is blank
 * @param outdoor whether the place is outdoors ({@code is_outdoor == 1})
 * @param verified whether {@code verified_by} is filled in
 */
public record Place(
    String placeId,
    String name,
    String placeType,
    double lat,
    double lon,
    String borough,
    Optional<String> openingHours,
    boolean outdoor,
    boolean verified) {}
