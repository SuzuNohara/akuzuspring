package com.nexus.dto;

import com.nexus.entity.MoodOption;

/**
 * Una opción del catálogo de ánimo tal como la ve el cliente (nexus-EMO-01, CU31).
 *
 * <p>No incluye {@code valence}/{@code arousal}: son la materia prima del promedio privado de CU40
 * (RN-28) y no deben salir por HTTP.
 */
public record MoodOptionResponse(String code, String label, String emoji, int sortOrder) {

    public static MoodOptionResponse from(MoodOption o) {
        return new MoodOptionResponse(o.getCode(), o.getLabel(), o.getEmoji(), o.getSortOrder());
    }
}
