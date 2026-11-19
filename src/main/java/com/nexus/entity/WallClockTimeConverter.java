package com.nexus.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * [horario-3am] Guarda una hora de reloj (columna TIME) como texto "HH:mm:ss", sin zona.
 *
 * <p>Con la JVM en America/Mexico_City y {@code hibernate.jdbc.time_zone=UTC}, Hibernate movia los
 * LocalTime 6 horas: guardaba 09:00 como 15:00 y leia el 09:00 del trigger de registro como 03:00.
 * Por eso el horario de fabrica se veia de 3 a 16. Como texto, MySQL lo guarda y devuelve igual.
 */
@Converter
public class WallClockTimeConverter implements AttributeConverter<LocalTime, String> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public String convertToDatabaseColumn(LocalTime time) {
        return time == null ? null : time.format(FORMAT);
    }

    @Override
    public LocalTime convertToEntityAttribute(String value) {
        return value == null ? null : LocalTime.parse(value.trim());
    }
}
