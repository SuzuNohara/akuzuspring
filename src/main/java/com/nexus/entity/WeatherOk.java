package com.nexus.entity;

/**
 * Clima favorable para una actividad (nexus-KB-02, K8). {@code ANY} = bajo techo, vale cualquier
 * clima (tormenta incluida). Toda actividad al aire libre declara un set distinto de {@code ANY}.
 */
public enum WeatherOk {
    ANY,
    CLOUDY,
    RAINY,
    SUNNY
}
