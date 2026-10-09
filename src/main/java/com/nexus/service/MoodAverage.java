package com.nexus.service;

/**
 * Promedio privado del ánimo de los últimos 7 días (nexus-EMO-01, CU40).
 *
 * <p><b>No es un DTO: nunca sale por HTTP</b> (RN-28 prohíbe que la pareja lo vea). Lo consume en
 * Java el agente de recomendación. {@code valence}/{@code arousal} en [-1, 1]; {@code days} es el
 * número de días con registro en la ventana (1..7). Un test ({@code NoMoodAverageEndpointTest})
 * vigila que ningún controlador lo devuelva ni lo reciba.
 */
public record MoodAverage(double valence, double arousal, int days) {}
