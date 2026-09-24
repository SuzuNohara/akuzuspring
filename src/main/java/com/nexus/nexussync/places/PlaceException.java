package com.nexus.nexussync.places;

import com.nexus.nexussync.NexussyncException;

/** Failure while assigning a place to an activity or parsing its opening hours (unit U9). */
public class PlaceException extends NexussyncException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an exception with a message and no cause.
   *
   * @param message human readable description of the failure
   * @implNote O(1) time and space.
   */
  public PlaceException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and its original cause.
   *
   * @param message human readable description of the failure
   * @param cause the exception that triggered this one
   * @implNote O(1) time and space.
   */
  public PlaceException(String message, Throwable cause) {
    super(message, cause);
  }
}
