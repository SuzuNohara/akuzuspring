package com.nexus.nexussync.places;

/** Outcome of the place assignment of one activity (§3.9). */
public enum PlaceStatus {
  /** At least one place option was found. */
  OK,
  /** No place fits even after every relaxation. */
  NO_PLACE,
  /** The activity happens at home: no place is needed. */
  HOME
}
