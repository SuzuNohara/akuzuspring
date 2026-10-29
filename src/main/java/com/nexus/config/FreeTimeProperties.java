package com.nexus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Aviso semanal de horarios libres juntos ([avisos-calendario] RF-29), prefijo {@code nexus.free-time}.
 *
 * @param enabled si se manda; apagado, nadie recibe el aviso
 * @param cron cuando se manda, en hora de la CDMX (lunes a las 9:00)
 * @param minMinutes minutos minimos de un horario libre para contarlo (como en "Detalles de la cita")
 */
@ConfigurationProperties(prefix = "nexus.free-time")
public record FreeTimeProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 0 9 * * MON") String cron,
        @DefaultValue("60") int minMinutes) {}
