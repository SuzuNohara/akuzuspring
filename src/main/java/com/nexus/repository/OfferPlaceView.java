package com.nexus.repository;

/**
 * [t06b-agente-java] U1 - Un enlace actividad × lugar con lo que necesita una oferta del agente
 * (proyeccion de activity_places + places).
 */
public interface OfferPlaceView {

    Long getActivityId();

    Long getPlaceId();

    String getName();

    String getBorough();

    Double getLat();

    Double getLon();

    String getAmbience();

    /** Costo por persona en este lugar; hoy no se usa (R9: la tarjeta y el filtro usan el de la actividad). */
    Integer getCostOverride();
}
