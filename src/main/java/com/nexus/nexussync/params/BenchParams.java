package com.nexus.nexussync.params;

/**
 * Parameters of the synthetic bench (unit U11).
 *
 * @param truthNoise standard deviation of the gaussian noise added to the truth score (A1)
 */
public record BenchParams(double truthNoise) {}
