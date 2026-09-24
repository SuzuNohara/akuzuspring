package com.nexus.nexussync.params;

import java.util.Map;

/**
 * Rubric rendered for the mediator agent (unit U5).
 *
 * @param criteria weight of each criterion; the weights must add up to 1
 * @param balance integer balance rules, such as the maximum activities per type
 * @param descarte penalty of each discard rule
 */
public record RubricParams(
    Map<String, Double> criteria, Map<String, Integer> balance, Map<String, Double> descarte) {

  /**
   * Copies the maps defensively so the record is immutable.
   *
   * @implNote O(c + b + d) time and space, the sizes of the three maps.
   */
  public RubricParams {
    criteria = Map.copyOf(criteria);
    balance = Map.copyOf(balance);
    descarte = Map.copyOf(descarte);
  }
}
