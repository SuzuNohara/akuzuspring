package com.nexus.entity;

/**
 * Banda de precio por pareja (nexus-KB-02, K2/DV-06). Queda <b>solo informativa</b> (visualización,
 * texto canónico y regla de vínculo L3): el filtro de presupuesto de AGENT-03 usa {@code
 * cost_max_mxn}, no la banda.
 */
public enum PriceBand {
    FREE,
    LOW,
    MID,
    HIGH,
    PREMIUM;

    /**
     * Banda del costo de pareja en MXN: 0 = FREE, 1–200 = LOW, 201–500 = MID, 501–1000 = HIGH,
     * &gt; 1000 = PREMIUM.
     *
     * @throws IllegalArgumentException si el costo es negativo
     */
    public static PriceBand ofCoupleCost(int mxn) {
        if (mxn < 0) {
            throw new IllegalArgumentException("costo de pareja negativo: " + mxn);
        }
        if (mxn == 0) {
            return FREE;
        }
        if (mxn <= 200) {
            return LOW;
        }
        if (mxn <= 500) {
            return MID;
        }
        if (mxn <= 1000) {
            return HIGH;
        }
        return PREMIUM;
    }
}
