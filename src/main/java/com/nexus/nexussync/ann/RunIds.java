package com.nexus.nexussync.ann;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds the {@code runId} that names a run everywhere: {@code .output/<runId>.md}, {@code
 * runs/<runId>/} and the {@code --id} passed to arkannie (A7).
 *
 * <p>Format: {@code <exp>-<couple>-<epochMillis>-<seed>}, where {@code exp} is the experiment name
 * slugged to {@code [a-z0-9-]} and the other parts are inserted as given.
 */
public final class RunIds {

  private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
  private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");
  private static final char SEPARATOR = '-';

  private RunIds() {}

  /**
   * Composes {@code <slug(experiment)>-<coupleId>-<epochMillis>-<seed>}.
   *
   * <p>The slug lower-cases the experiment name ({@link Locale#ROOT}), collapses every run of
   * characters outside {@code [a-z0-9]} into one dash and trims dashes at both ends.
   *
   * @param experiment experiment name, must contain at least one ASCII letter or digit
   * @param coupleId couple identifier ({@code min(a,b)-max(a,b)}), inserted verbatim
   * @param epochMillis start of the run in milliseconds since the epoch
   * @param seed seed of the run's {@code Random}
   * @return the run identifier
   * @throws IllegalArgumentException if the experiment slug is empty
   * @implNote O(n) time and space in the length of {@code experiment}.
   */
  public static String of(String experiment, String coupleId, long epochMillis, long seed) {
    String slug = slug(experiment);
    if (slug.isEmpty()) {
      throw new IllegalArgumentException(
          "experiment name has no ASCII letters or digits: '" + experiment + "'");
    }
    return new StringBuilder(slug)
        .append(SEPARATOR)
        .append(coupleId)
        .append(SEPARATOR)
        .append(epochMillis)
        .append(SEPARATOR)
        .append(seed)
        .toString();
  }

  private static String slug(String experiment) {
    String collapsed = NON_SLUG.matcher(experiment.toLowerCase(Locale.ROOT)).replaceAll("-");
    return EDGE_DASHES.matcher(collapsed).replaceAll("");
  }
}
