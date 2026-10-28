package com.nexus.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Notificaciones push de los avisos de la campanita ([push-avisos]), prefijo {@code nexus.push}.
 *
 * @param enabled si se mandan; apagado, los avisos solo se guardan en la campanita
 * @param url servicio de push de Expo, que reenvia a FCM
 * @param timeout tiempo maximo del envio; si se excede, el aviso se queda solo en la campanita
 */
@ConfigurationProperties(prefix = "nexus.push")
public record PushProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://exp.host/--/api/v2/push/send") URI url,
        @DefaultValue("5s") Duration timeout) {}
