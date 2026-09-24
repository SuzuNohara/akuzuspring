package com.nexus.nexussync.catalog;

/**
 * Where an activity happens: at home (no place needed) or somewhere in the city (kb/schema.md §3).
 */
public enum LocationScope {
  /** The activity happens at home; {@code place_types} is empty (rule V13). */
  HOME,
  /** The activity happens at a place of the city; {@code place_types} is not empty (rule V13). */
  CITY
}
