package com.nexus.nexussync.params;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parameters of the two-round gate (unit U7).
 *
 * <p>The design names {@code kPick} and {@code kVote} are exposed as {@code pickCount} and {@code
 * voteCount} because Google style forbids a one-letter camel-case prefix; the YAML keys stay {@code
 * k_pick} and {@code k_vote}.
 *
 * @param pickCount activities each agent picks in round one ({@code k_pick})
 * @param voteCount activities each persona votes in round two ({@code k_vote})
 * @param finalSize size of the final list
 * @param intersection intersection rule of round one
 * @param thresholdR1 minimum intersection size that closes in round one
 * @param fill strategy that fills a short intersection
 * @param rankAggregation aggregation of the individual rankings
 * @param maxRounds maximum number of rounds
 * @param allowTwoAi whether the gate degrades to two agents when a persona fails
 */
public record RoundsParams(
    @JsonProperty("k_pick") int pickCount,
    @JsonProperty("k_vote") int voteCount,
    int finalSize,
    Intersection intersection,
    int thresholdR1,
    Fill fill,
    RankAggregation rankAggregation,
    int maxRounds,
    boolean allowTwoAi) {}
