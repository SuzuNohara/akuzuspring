package com.nexus.entity;

/**
 * Categoría del banco de ideas que ve el usuario (nexus-KB-02, CU34 L10516). El orden es el literal
 * del TT.
 */
public enum BankCategory {
    COMER_Y_BEBER("Comer y beber"),
    CULTURA_Y_ESPECTACULOS("Cultura y espectáculos"),
    AIRE_LIBRE("Aire libre"),
    DEPORTE_Y_MOVIMIENTO("Deporte y movimiento"),
    APRENDER_JUNTOS("Aprender juntos"),
    JUEGOS_Y_RELAX("Juegos y relax"),
    OTRAS_IDEAS("Otras ideas");

    private final String label;

    BankCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
