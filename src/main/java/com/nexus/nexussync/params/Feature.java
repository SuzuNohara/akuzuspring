package com.nexus.nexussync.params;

/** Scoring feature of the sampler; each one is a component of the weight vector. */
public enum Feature {
  INTEREST,
  PRICE,
  DISTANCE,
  NOVELTY,
  EMOTION,
  COLLAB,
  SEASON
}
