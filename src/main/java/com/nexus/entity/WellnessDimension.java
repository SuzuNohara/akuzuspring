package com.nexus.entity;

/**
 * Dimensión de bienestar interna para el agente (nexus-KB-02, Tabla 37 L11308-11314, K1/DV-05). Se
 * asigna por actividad y la confirma un humano; nunca se deriva de los intereses.
 */
public enum WellnessDimension {
    FISICA("Física"),
    EMOCIONAL("Emocional"),
    INTELECTUAL("Intelectual"),
    SOCIAL("Social"),
    ESPIRITUAL("Espiritual"),
    AMBIENTAL("Ambiental"),
    PROFESIONAL("Profesional");

    private final String label;

    WellnessDimension(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
