package com.nexus.repository;

/** Un lugar posible de una actividad, con sus coordenadas (proyeccion de activity_places + places). */
public interface ActivityPlaceView {

    Long getActivityId();

    Long getPlaceId();

    String getName();

    String getBorough();

    Double getLat();

    Double getLon();
}
