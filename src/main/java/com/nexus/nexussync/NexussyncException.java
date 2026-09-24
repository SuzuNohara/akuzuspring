package com.nexus.nexussync;

/**
 * Base checked exception of the nexussync package.
 *
 * <p>Every unit of the package raises a subclass of this exception ({@code ParamsException}, {@code
 * CatalogException}, {@code ContextException}, {@code AnnException}, {@code DecisionException},
 * {@code PlaceException}), so callers can catch the whole hierarchy with a single clause. Expected
 * outcomes (for example {@code INSUFFICIENT_SAMPLE} or {@code AI_UNAVAILABLE}) are status enums,
 * not exceptions.
 */
public class NexussyncException extends Exception {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an exception with a message and no cause.
   *
   * @param message human readable description of the failure
   * @implNote O(1) time and space.
   */
  public NexussyncException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and its original cause.
   *
   * @param message human readable description of the failure
   * @param cause the exception that triggered this one
   * @implNote O(1) time and space.
   */
  public NexussyncException(String message, Throwable cause) {
    super(message, cause);
  }
}
