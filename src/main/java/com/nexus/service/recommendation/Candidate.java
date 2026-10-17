package com.nexus.service.recommendation;

import com.nexus.entity.Activity;

/**
 * Una actividad elegida para recomendar.
 *
 * @param place lugar en la ciudad; {@code null} para actividades en casa
 * @param score afinidad con la pareja; mayor es mejor
 */
public record Candidate(Activity activity, PlaceChoice place, double score) {}
