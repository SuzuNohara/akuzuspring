package com.nexus.integration.expo;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.config.PushProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente del servicio de push de Expo, que reenvia a FCM ([push-avisos]).
 *
 * <p>Nunca lanza excepcion: una falla de red o de Expo da {@link PushResult#FAILED} y el aviso se
 * queda solo en la campanita (R3).
 */
@Slf4j
public class ExpoPushClient {

    /** Error de Expo cuando el token ya no existe (app desinstalada o token viejo). */
    static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

    private static final String CHANNEL_ID = "default";

    private final RestClient http;
    private final String url;

    public ExpoPushClient(RestClient.Builder builder, PushProperties properties) {
        this.http = builder.build();
        this.url = properties.url().toString();
    }

    /** Push que se manda a un telefono. */
    public record PushMessage(String to, String title, String body, Map<String, String> data) {}

    /** Resultado del envio. */
    public enum PushResult {
        SENT,
        /** El token ya no sirve y hay que quitarlo del usuario (R8). */
        TOKEN_INVALID,
        FAILED
    }

    public PushResult send(PushMessage message) {
        try {
            JsonNode response = http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload(message))
                    .retrieve()
                    .body(JsonNode.class);
            return resultOf(response);
        } catch (RestClientException e) {
            log.warn("Expo no recibio la push: {}", e.getMessage());
            return PushResult.FAILED;
        }
    }

    private static Map<String, Object> payload(PushMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", message.to());
        payload.put("title", message.title());
        payload.put("body", message.body());
        payload.put("data", message.data() == null ? Map.of() : message.data());
        payload.put("channelId", CHANNEL_ID);
        payload.put("sound", "default");
        payload.put("priority", "high");
        return payload;
    }

    /** Expo responde {@code {"data": {...}}}; si se mando como lista, {@code data} es lista. */
    private static PushResult resultOf(JsonNode response) {
        JsonNode ticket = response == null ? null : response.path("data");
        if (ticket != null && ticket.isArray()) {
            ticket = ticket.path(0);
        }
        if (ticket == null || ticket.isMissingNode()) {
            log.warn("Expo respondio sin ticket: {}", response);
            return PushResult.FAILED;
        }
        if ("ok".equals(ticket.path("status").asText())) {
            return PushResult.SENT;
        }
        if (DEVICE_NOT_REGISTERED.equals(ticket.path("details").path("error").asText())) {
            return PushResult.TOKEN_INVALID;
        }
        log.warn("Expo rechazo la push: {}", ticket.path("message").asText());
        return PushResult.FAILED;
    }
}
