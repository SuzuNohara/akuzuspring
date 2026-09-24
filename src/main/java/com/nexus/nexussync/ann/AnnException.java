package com.nexus.nexussync.ann;

import com.nexus.nexussync.NexussyncException;
import java.util.Objects;

/**
 * Failure while rendering, launching or reading an Ann program run by arkannie (unit U6).
 *
 * <p>The {@link Kind} tells the caller which stage failed so it can decide between retrying,
 * degrading the gate or aborting the run.
 */
public class AnnException extends NexussyncException {

  private static final long serialVersionUID = 1L;

  /** Stage or condition that produced the failure. */
  public enum Kind {
    /** A template, binary, output file or run directory was not found. */
    NOT_FOUND,
    /** The arkannie output could not be parsed. */
    PARSE,
    /** arkannie finished with a non-zero exit code. */
    EXIT,
    /** arkannie exceeded its timeout and was destroyed. */
    TIMEOUT,
    /** Another run holds the {@code .run.lock} of the nexussync directory. */
    LOCKED,
    /** A template placeholder was left without a value. */
    RENDER,
    /** The {@code .agents/} directory does not contain exactly the expected agents. */
    AGENTS
  }

  private final Kind kind;

  /**
   * Creates an exception of the given kind with a message and no cause.
   *
   * @param kind stage or condition that failed, never {@code null}
   * @param message human readable description of the failure
   * @implNote O(1) time and space.
   */
  public AnnException(Kind kind, String message) {
    super(message);
    this.kind = Objects.requireNonNull(kind, "kind");
  }

  /**
   * Creates an exception of the given kind with a message and its original cause.
   *
   * @param kind stage or condition that failed, never {@code null}
   * @param message human readable description of the failure
   * @param cause the exception that triggered this one
   * @implNote O(1) time and space.
   */
  public AnnException(Kind kind, String message, Throwable cause) {
    super(message, cause);
    this.kind = Objects.requireNonNull(kind, "kind");
  }

  /**
   * Returns the stage or condition that produced this failure.
   *
   * @return the kind given at construction, never {@code null}
   * @implNote O(1) time and space.
   */
  public Kind kind() {
    return kind;
  }
}
