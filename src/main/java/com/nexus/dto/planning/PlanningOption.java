package com.nexus.dto.planning;

import com.nexus.dto.RecommendationItem;

/** B3 - Una opcion con el voto de cada quien ({@code LIKE}, {@code DISLIKE} o {@code null}). */
public record PlanningOption(RecommendationItem item, String myVote, String partnerVote) {}
