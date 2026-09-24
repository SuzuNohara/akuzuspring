package com.nexus.nexussync.catalog;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Parser of the set columns of the knowledge base CSV files, serialized as {@code A|B|C}
 * (kb/schema.md §7, same convention as {@code tools/kb}).
 */
public final class CsvSets {

  private static final String SEPARATOR = "\\|";

  private CsvSets() {}

  /**
   * Parses a pipe separated set. Elements are trimmed, empty elements are dropped and duplicates
   * collapse; an empty or blank text yields the empty set.
   *
   * @param pipeSeparated the raw column value, for example {@code "AFTERNOON|MORNING"}
   * @return an unmodifiable set whose iteration order is alphabetical
   * @implNote O(k log k) time and O(k) space, k = number of elements.
   */
  public static Set<String> parse(String pipeSeparated) {
    Objects.requireNonNull(pipeSeparated, "pipeSeparated");
    if (pipeSeparated.isBlank()) {
      return Set.of();
    }
    Set<String> out = new TreeSet<>();
    for (String part : pipeSeparated.split(SEPARATOR)) {
      String value = part.trim();
      if (!value.isEmpty()) {
        out.add(value);
      }
    }
    return Collections.unmodifiableSet(out);
  }
}
