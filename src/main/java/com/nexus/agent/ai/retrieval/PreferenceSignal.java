package com.nexus.agent.ai.retrieval;

import java.util.regex.Pattern;

/**
 * Señal de preferencia de una persona para construir el texto de consulta (nexus-AGENT-00, R25). Se
 * construye desde {@code Preference.getName()} + {@code UserPreference.getLevel()}, <b>nunca</b>
 * desde las notas libres (entrada cerrada, RN-32).
 */
public record PreferenceSignal(String name, int level) {

    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{N} ,.()/'-]{1,120}$");

    public PreferenceSignal {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("nombre de preferencia inválido: " + name);
        }
        if (level < 0 || level > 100) {
            throw new IllegalArgumentException("nivel fuera de [0,100]: " + level);
        }
    }
}
