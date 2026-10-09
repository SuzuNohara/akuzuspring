package com.nexus.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Un registro de ánimo de un día concreto tal como lo ve el cliente (nexus-EMO-01, CU31/CU32).
 */
public record MoodEntryResponse(
        LocalDate date, String code, String label, String emoji, Instant updatedAt) {}
