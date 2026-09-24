package com.nexus.nexussync.ann;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Outcome of one external process launched through a {@link ProcessLauncher}.
 *
 * <p>Build instances with {@link #of(int, String, String, boolean, Duration)}, which caps each
 * captured stream at {@value #MAX_STREAM_BYTES} bytes so a runaway process cannot bloat the run
 * traces; the canonical constructor keeps whatever it is given.
 *
 * @param exitCode exit status of the process (meaningless when {@code timedOut})
 * @param stdout captured standard output, possibly ending in {@value #TRUNCATED_MARK}
 * @param stderr captured standard error, possibly ending in {@value #TRUNCATED_MARK}
 * @param timedOut whether the process was destroyed for exceeding its timeout
 * @param elapsed wall-clock time between start and termination
 */
public record LaunchResult(
    int exitCode, String stdout, String stderr, boolean timedOut, Duration elapsed) {

  /** Maximum number of UTF-8 bytes kept per stream: 64 KiB. */
  public static final int MAX_STREAM_BYTES = 65_536;

  /** Suffix appended to a stream that was cut at {@link #MAX_STREAM_BYTES}. */
  public static final String TRUNCATED_MARK = "[truncated]";

  private static final int CONTINUATION_MASK = 0xC0;
  private static final int CONTINUATION_TAG = 0x80;

  /**
   * Validates that no component is {@code null}.
   *
   * @implNote O(1) time and space.
   */
  public LaunchResult {
    Objects.requireNonNull(stdout, "stdout");
    Objects.requireNonNull(stderr, "stderr");
    Objects.requireNonNull(elapsed, "elapsed");
  }

  /**
   * Creates a result whose streams are truncated to {@link #MAX_STREAM_BYTES} UTF-8 bytes.
   *
   * <p>A stream that exceeds the limit is cut on a character boundary (never inside a multi-byte
   * sequence) and {@link #TRUNCATED_MARK} is appended after the cut.
   *
   * @param exitCode exit status of the process
   * @param stdout captured standard output, never {@code null}
   * @param stderr captured standard error, never {@code null}
   * @param timedOut whether the process was destroyed for exceeding its timeout
   * @param elapsed wall-clock time between start and termination, never {@code null}
   * @return the result with both streams capped
   * @implNote O(n) time and space in the total length of both streams.
   */
  public static LaunchResult of(
      int exitCode, String stdout, String stderr, boolean timedOut, Duration elapsed) {
    return new LaunchResult(exitCode, truncate(stdout), truncate(stderr), timedOut, elapsed);
  }

  private static String truncate(String stream) {
    byte[] bytes = stream.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= MAX_STREAM_BYTES) {
      return stream;
    }
    // Step back over continuation bytes so the cut never splits a character. The encoding of a
    // String is always valid UTF-8, so byte 0 is a lead byte and the loop terminates.
    int cut = MAX_STREAM_BYTES;
    while ((bytes[cut] & CONTINUATION_MASK) == CONTINUATION_TAG) {
      cut--;
    }
    return new String(bytes, 0, cut, StandardCharsets.UTF_8) + TRUNCATED_MARK;
  }
}
