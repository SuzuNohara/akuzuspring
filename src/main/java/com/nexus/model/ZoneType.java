package com.nexus.model;

/** RF-36 / RN-31 - Zona de la cita elegida al pedir una recomendacion (CU36, CU41). */
public enum ZoneType {
    /** A 15 km de la ubicacion actual de quien pide; la ubicacion no se guarda (RN-30). */
    NEAR_US,
    /** A 15 km del centro de una alcaldia. */
    BOROUGH,
    /** Sin filtro de distancia. */
    NONE
}
