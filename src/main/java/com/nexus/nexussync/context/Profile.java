package com.nexus.nexussync.context;

import com.nexus.nexussync.params.Feature;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the pipeline knows about one person of the couple (unit U3).
 *
 * @param userId identifier of the user
 * @param borough borough (alcaldía) of the user
 * @param location home location, when known
 * @param preferences interest slug of {@code kb/interests.yml} to level 1–5
 * @param emotionalRecent recent emotional records
 * @param history past offers of activities
 * @param constraints budget, travel and window constraints
 * @param truthWeights hidden preference weights used only by the bench oracle
 */
public record Profile(
    int userId,
    String borough,
    Optional<Location> location,
    Map<String, Integer> preferences,
    List<EmotionEntry> emotionalRecent,
    List<HistoryEntry> history,
    Constraints constraints,
    Optional<Map<Feature, Double>> truthWeights) {

  /**
   * Copies every collection so the record is immutable.
   *
   * @implNote O(n) time and space in the size of the collections.
   */
  public Profile {
    preferences = Map.copyOf(preferences);
    emotionalRecent = List.copyOf(emotionalRecent);
    history = List.copyOf(history);
    truthWeights = truthWeights.map(Map::copyOf);
  }
}
