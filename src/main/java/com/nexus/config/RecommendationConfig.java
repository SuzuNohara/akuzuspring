package com.nexus.config;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [kb-lugares] U7 - Azar de las recomendaciones: con "Sin zona" el lugar se elige al azar entre
 * los mas populares de la actividad. Inyectarlo deja probarlo con una semilla fija.
 *
 * <p>{@link SecureRandom} porque el bean se comparte entre solicitudes y es seguro entre hilos
 * ({@code SplittableRandom} no lo es); el costo no importa, es una llamada por actividad.
 */
@Configuration
public class RecommendationConfig {

    @Bean
    public RandomGenerator recommendationRandom() {
        return new SecureRandom();
    }
}
