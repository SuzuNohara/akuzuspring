package com.nexus.agent.ai.catalog;

/**
 * Vocabulario del índice para la categoría del banco (nexus-AGENT-00, D-10, CU34). Capa
 * anticorrupción: propio del motor, independiente de las entidades JPA de KB-02.
 */
public enum BankCategory {
    COMER_Y_BEBER("Comer y beber"),
    CULTURA_Y_ESPECTACULOS("Cultura y espectáculos"),
    AIRE_LIBRE("Aire libre"),
    DEPORTE_Y_MOVIMIENTO("Deporte y movimiento"),
    APRENDER_JUNTOS("Aprender juntos"),
    JUEGOS_Y_RELAX("Juegos y relax"),
    OTRAS("Otras");

    private final String label;

    BankCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
