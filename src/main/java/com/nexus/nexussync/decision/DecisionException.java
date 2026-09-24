package com.nexus.nexussync.decision;

import com.nexus.nexussync.NexussyncException;

/** Failure while resolving the couple decision or ranking the final options (unit U8). */
public class DecisionException extends NexussyncException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an exception with a message and no cause.
   *
   * @param message human readable description of the failure
   * @implNote O(1) time and space.
   */
  public DecisionException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and its original cause.
   *
   * @param message human readable description of the failure
   * @param cause the exception that triggered this one
   * @implNote O(1) time and space.
   */
  public DecisionException(String message, Throwable cause) {
    super(message, cause);
  }
}
