package com.nexus.nexussync.context;

import com.nexus.nexussync.NexussyncException;

/** Failure while loading a profile or building the couple context (unit U3). */
public class ContextException extends NexussyncException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an exception with a message and no cause.
   *
   * @param message human readable description of the failure
   * @implNote O(1) time and space.
   */
  public ContextException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and its original cause.
   *
   * @param message human readable description of the failure
   * @param cause the exception that triggered this one
   * @implNote O(1) time and space.
   */
  public ContextException(String message, Throwable cause) {
    super(message, cause);
  }
}
