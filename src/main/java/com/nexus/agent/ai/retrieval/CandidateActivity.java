package com.nexus.agent.ai.retrieval;

import com.nexus.agent.ai.catalog.BankCategory;
import com.nexus.agent.ai.catalog.LocationScope;

/**
 * Una candidata recuperada del índice (nexus-AGENT-00): la actividad en un lugar, con su score de
 * similitud. Expone costo y duración como rango (min/max) y el ámbito, que es lo que el compositor y
 * AGENT-02 necesitan.
 */
public record CandidateActivity(
        String activityId,
        String title,
        String description,
        BankCategory bankCategory,
        int costMin,
        int costMax,
        int durationMin,
        int durationMax,
        LocationScope scope,
        String placeId,
        String placeName,
        String borough,
        double score) {}
