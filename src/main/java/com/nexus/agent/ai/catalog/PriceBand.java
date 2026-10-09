package com.nexus.agent.ai.catalog;

/**
 * Vocabulario del índice para el precio (nexus-AGENT-00, D-10). Bandas por persona de {@code
 * kb/schema.md} §3. Propio del motor; no confundir con {@code com.nexus.entity.PriceBand} (por
 * pareja, KB-02).
 */
public enum PriceBand {
    FREE,
    LOW,
    MID,
    HIGH,
    PREMIUM;

    /** Banda por costo por persona en MXN: 0 / 1–200 / 201–800 / 801–3000 / ≥3001. */
    public static PriceBand of(int mxnPerPerson) {
        if (mxnPerPerson <= 0) {
            return FREE;
        }
        if (mxnPerPerson <= 200) {
            return LOW;
        }
        if (mxnPerPerson <= 800) {
            return MID;
        }
        if (mxnPerPerson <= 3000) {
            return HIGH;
        }
        return PREMIUM;
    }
}
