package com.nexus.dto;

import java.time.LocalDate;

/**
 * Una casilla del resumen de 7 días (nexus-EMO-01, CU32). {@code code} y {@code emoji} en {@code
 * null} cuando ese día no tiene registro.
 */
public record MoodDaySummary(LocalDate date, String code, String emoji) {}
