package com.nexus.entity;

import jakarta.persistence.Converter;

/** Convierte {@code Set<WeatherOk>} a/desde la columna de texto (nexus-KB-02). */
@Converter
public final class WeatherOkSetConverter extends EnumSetConverter<WeatherOk> {
    public WeatherOkSetConverter() {
        super(WeatherOk.class);
    }
}
