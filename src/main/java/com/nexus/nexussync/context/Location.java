package com.nexus.nexussync.context;

/**
 * Geographic point of a profile (WGS84 degrees).
 *
 * @param lat latitude in degrees
 * @param lon longitude in degrees
 */
public record Location(double lat, double lon) {}
