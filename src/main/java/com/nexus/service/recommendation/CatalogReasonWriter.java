package com.nexus.service.recommendation;

import com.nexus.entity.Activity;

/**
 * CU40 FA02 - Frase de cada recomendacion tomada del catalogo ({@code activities.rationale}). Es la
 * frase de las recomendaciones por reglas y el respaldo de cada frase invalida del agente.
 */
public final class CatalogReasonWriter {

    /** Largo maximo que se muestra (la guardia final del agente usa el mismo). */
    public static final int MAX_LENGTH = 200;

    private static final String ELLIPSIS = "…";

    private CatalogReasonWriter() {}

    /** Justificacion del catalogo, o la descripcion si no hay, recortada a {@link #MAX_LENGTH}. */
    public static String reasonOf(Activity a) {
        String text = a.getRationale() == null || a.getRationale().isBlank() ? a.getDescription() : a.getRationale();
        String trimmed = text == null ? "" : text.strip();
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH - 1) + ELLIPSIS;
    }
}
