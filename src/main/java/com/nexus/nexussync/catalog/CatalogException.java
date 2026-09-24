package com.nexus.nexussync.catalog;

import com.nexus.nexussync.NexussyncException;

/** Failure while loading the activity, place or link CSV files of the catalog (unit U2). */
public class CatalogException extends NexussyncException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an exception with a message and no cause.
   *
   * @param message human readable description of the failure
   * @implNote O(1) time and space.
   */
  public CatalogException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and its original cause.
   *
   * @param message human readable description of the failure
   * @param cause the exception that triggered this one
   * @implNote O(1) time and space.
   */
  public CatalogException(String message, Throwable cause) {
    super(message, cause);
  }
}
