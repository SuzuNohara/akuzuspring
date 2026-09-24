package com.nexus.nexussync.context;

/**
 * Aggregated emotional climate of a person.
 *
 * @param valence level of the valence axis
 * @param energy level of the energy axis
 */
public record Climate(Level valence, Level energy) {}
