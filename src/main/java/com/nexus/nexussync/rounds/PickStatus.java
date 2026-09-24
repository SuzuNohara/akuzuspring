package com.nexus.nexussync.rounds;

/** Outcome of one agent call once parsed (unit U7). */
public enum PickStatus {
  /** At least one valid id was returned. */
  OK,
  /** No envelope, a non-success status, an unreadable payload or zero valid ids. */
  FAILED,
  /** The agent exceeded its timeout. */
  TIMEOUT
}
