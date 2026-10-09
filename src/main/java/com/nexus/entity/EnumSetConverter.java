package com.nexus.entity;

import jakarta.persistence.AttributeConverter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Convierte un {@code Set} de enum a/desde una columna de texto con los nombres ordenados y unidos
 * por {@code |} (nexus-KB-02). Orden estable para que la columna sea determinista. Un {@code null} o
 * un set vacío se guardan como cadena vacía.
 */
public abstract class EnumSetConverter<E extends Enum<E>> implements AttributeConverter<Set<E>, String> {

    private final Class<E> type;

    protected EnumSetConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(Set<E> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }
        return attribute.stream()
                .map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new))
                .stream()
                .collect(Collectors.joining("|"));
    }

    @Override
    public Set<E> convertToEntityAttribute(String dbData) {
        EnumSet<E> set = EnumSet.noneOf(type);
        if (dbData == null || dbData.isEmpty()) {
            return Collections.unmodifiableSet(set);
        }
        for (String token : dbData.split("\\|")) {
            if (token.isEmpty()) {
                continue;
            }
            try {
                set.add(Enum.valueOf(type, token));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "valor desconocido en " + type.getSimpleName() + ": " + token);
            }
        }
        return Collections.unmodifiableSet(set);
    }
}
