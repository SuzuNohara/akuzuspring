package com.nexus.repository;

/** Un lugar posible de una actividad, con sus coordenadas (proyeccion de activity_places + places). */
public interface ActivityPlaceView {

    Long getActivityId();

    Long getPlaceId();

    String getName();

    String getBorough();

    Double getLat();

    Double getLon();

    /** [kb-lugares] Popularidad del lugar ({@code places.score}); {@code null} sin dato. */
    Double getScore();
}
