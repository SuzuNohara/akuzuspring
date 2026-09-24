package com.nexus.nexussync.context;

/** Discrete level of one axis of the emotional climate. */
public enum Level {
  /** Mean below -0.33. */
  LOW,
  /** Mean between -0.33 and 0.33, both included. */
  MID,
  /** Mean above 0.33. */
  HIGH,
  /** No valid entry in the window. */
  UNKNOWN
}
