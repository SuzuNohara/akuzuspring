package com.nexus.nexussync.params;

import com.nexus.nexussync.NexussyncException;

/** Failure while loading, merging or validating the experiment parameters (unit U1). */
public class ParamsException extends NexussyncException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an exception with a message and no cause.
   *
   * @param message human readable description of the failure
   * @implNote O(1) time and space.
   */
  public ParamsException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and its original cause.
   *
   * @param message human readable description of the failure
   * @param cause the exception that triggered this one
   * @implNote O(1) time and space.
   */
  public ParamsException(String message, Throwable cause) {
    super(message, cause);
  }
}
