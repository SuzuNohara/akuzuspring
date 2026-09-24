package com.nexus.nexussync.params;

/**
 * Windows used to build the couple context (unit U3).
 *
 * @param emotionWindowDays days of emotional entries considered for the climate
 * @param historyWindowDays days of history considered for novelty and collaboration
 */
public record ContextParams(int emotionWindowDays, int historyWindowDays) {}
