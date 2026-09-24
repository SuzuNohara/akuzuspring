package com.nexus.nexussync.places;

import com.nexus.nexussync.context.Location;

/**
 * Geometry helpers of the place assignment, consistent with {@code tools/kb/geo.py} (§3.9).
 *
 * <p>Uses the same mean Earth radius as the Python haversine (6 371 008.8 m).
 */
public final class Geo {

  /** Mean Earth radius in kilometres, identical to {@code tools/kb/geo.py}. */
  static final double EARTH_RADIUS_KM = 6371.0088;

  private static final double HALF = 0.5;

  private Geo() {}

  /**
   * Great-circle distance between two WGS84 points using the haversine formula.
   *
   * @param lat1 latitude of the first point in degrees
   * @param lon1 longitude of the first point in degrees
   * @param lat2 latitude of the second point in degrees
   * @param lon2 longitude of the second point in degrees
   * @return distance in kilometres
   * @implNote O(1) time and space.
   */
  public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
    double phi1 = Math.toRadians(lat1);
    double phi2 = Math.toRadians(lat2);
    double deltaPhi = Math.toRadians(lat2 - lat1);
    double deltaLambda = Math.toRadians(lon2 - lon1);
    double sinPhi = Math.sin(deltaPhi * HALF);
    double sinLambda = Math.sin(deltaLambda * HALF);
    double h = sinPhi * sinPhi + Math.cos(phi1) * Math.cos(phi2) * sinLambda * sinLambda;
    return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(Math.min(1.0, h)));
  }

  /**
   * Distance between two locations.
   *
   * @param a first location
   * @param b second location
   * @return distance in kilometres
   * @implNote O(1) time and space.
   */
  public static double distanceKm(Location a, Location b) {
    return haversineKm(a.lat(), a.lon(), b.lat(), b.lon());
  }

  /**
   * Midpoint of two locations as the arithmetic mean of latitude and longitude.
   *
   * <p>Accurate to well under a metre for the urban distances of the catalog (&lt; 20 km).
   *
   * @param a first location
   * @param b second location
   * @return the midpoint
   * @implNote O(1) time and space.
   */
  public static Location midpoint(Location a, Location b) {
    return new Location((a.lat() + b.lat()) * HALF, (a.lon() + b.lon()) * HALF);
  }
}
