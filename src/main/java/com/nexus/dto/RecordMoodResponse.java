package com.nexus.dto;

/**
 * Resultado de registrar el ánimo de hoy (nexus-EMO-01). {@code replaced} distingue un alta nueva
 * de un reemplazo del mismo día (RN-38).
 */
public record RecordMoodResponse(MoodEntryResponse entry, boolean replaced) {}
