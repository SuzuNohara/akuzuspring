package com.nexus.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicacion en hora de la Ciudad de Mexico.
 *
 * <p>Las reglas que dependen del "dia" (p. ej. RN-38: un registro emocional por dia) usan el dia
 * de la CDMX, no el de UTC; inyectar el {@link Clock} permite probarlas con una hora fija.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId APP_ZONE = ZoneId.of("America/Mexico_City");

    @Bean
    public Clock clock() {
        return Clock.system(APP_ZONE);
    }
}
