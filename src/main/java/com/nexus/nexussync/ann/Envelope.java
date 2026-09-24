package com.nexus.nexussync.ann;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One agent result returned by an Ann program (unit U6).
 *
 * <p>Each {@code [return] --id=r $result} inside a {@code parallel ... each} block produces a yaml
 * block {@code {id, status, payload}} in {@code .output/<runId>.md}; this record is that block. The
 * {@code id} is the {@code --id} of the dispatch ({@code a}, {@code b} or {@code m}), never the
 * name of the section, because sections are written in completion order.
 *
 * @param id dispatch identifier of the agent call
 * @param status status reported by arkannie for that call (for example {@code ok})
 * @param payload structured result of the agent, as parsed from yaml; empty if none
 */
public record Envelope(String id, String status, Map<String, Object> payload) {

  /**
   * Validates the components and copies the payload so the record is immutable.
   *
   * @implNote O(n) time and space, n = number of top-level payload entries.
   */
  public Envelope {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(status, "status");
    payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
  }
}
