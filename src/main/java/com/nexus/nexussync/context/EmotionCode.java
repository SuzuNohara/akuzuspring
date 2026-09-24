package com.nexus.nexussync.context;

import java.util.Optional;

/** Emotion codes understood by the {@link ClimateCalculator}. */
public enum EmotionCode {
  HAPPY,
  CALM,
  GRATEFUL,
  NEUTRAL,
  SAD,
  STRESSED,
  ANGRY,
  TIRED;

  /**
   * Parses a code ignoring case and surrounding blanks.
   *
   * @param code text of the code
   * @return the code, or empty when the text is not a known code
   * @implNote O(k) time with k the number of codes, O(1) space.
   */
  public static Optional<EmotionCode> parse(String code) {
    String trimmed = code.trim();
    for (EmotionCode candidate : values()) {
      if (candidate.name().equalsIgnoreCase(trimmed)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }
}
