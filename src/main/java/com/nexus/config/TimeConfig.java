package com.nexus.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación fijado a la zona de operación (nexus-EMO-01, RN-38).
 *
 * <p>El "día natural" de un registro de ánimo depende de la zona horaria, no del reloj de la JVM
 * ni del teléfono. Inyectar un {@link Clock} permite además probar la medianoche con {@code
 * Clock.fixed} sin esperar a que llegue. La misma zona ya la usa {@code AvailabilityService}.
 */
@Configuration
public class TimeConfig {

    /** Zona de operación: Ciudad de México (UTC−6 todo el año desde 2022). */
    public static final ZoneId APP_ZONE = ZoneId.of("America/Mexico_City");

    @Bean
    public Clock appClock() {
        return Clock.system(APP_ZONE);
    }
}
