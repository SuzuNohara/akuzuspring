package com.nexus.exception;

/**
 * El servicio del clima no respondio, respondio con error, excedio el tiempo o mando otra forma.
 * Quien la recibe recomienda sin filtrar por clima: el clima nunca hace fallar una solicitud.
 */
public class WeatherUnavailableException extends RuntimeException {

    public WeatherUnavailableException(String message) {
        super(message);
    }

    public WeatherUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
