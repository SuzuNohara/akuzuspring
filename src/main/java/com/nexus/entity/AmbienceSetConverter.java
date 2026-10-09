package com.nexus.entity;

import jakarta.persistence.Converter;

/** Convierte {@code Set<Ambience>} a/desde la columna de texto (nexus-KB-02). */
@Converter
public final class AmbienceSetConverter extends EnumSetConverter<Ambience> {
    public AmbienceSetConverter() {
        super(Ambience.class);
    }
}
