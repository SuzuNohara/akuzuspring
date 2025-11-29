package com.nexus.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Envía una notificación push cuando se establece un vínculo
     * 
     * @param expoPushToken Token de Expo Push del usuario que generó el código
     * @param partnerName Nombre del usuario que usó el código
     */
    public void sendLinkEstablishedNotification(String expoPushToken, String partnerName) {
        if (expoPushToken == null || expoPushToken.isEmpty()) {
            log.warn("⚠️ No se puede enviar notificación: Push token vacío");
            return;
        }

        try {
            log.info("🔔 Preparando notificación de vínculo establecido");
            log.info("🔔 Destinatario: {}", partnerName);
            log.info("🔔 Token Push: {}...", expoPushToken.substring(0, Math.min(30, expoPushToken.length())));
            
            // Construir el payload para Expo Push Notifications
            Map<String, Object> notification = new HashMap<>();
            notification.put("to", expoPushToken);
            notification.put("sound", "default");
            notification.put("title", "¡Vínculo establecido!");
            notification.put("body", partnerName + " acaba de conectarse contigo en Nexus ❤️");
            notification.put("priority", "high");
            
            // Datos personalizados
            Map<String, String> data = new HashMap<>();
            data.put("type", "LINK_ESTABLISHED");
            data.put("partnerName", partnerName);
            notification.put("data", data);

            // Configurar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            headers.set("Accept-Encoding", "gzip, deflate");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(notification, headers);

            // Enviar la notificación a Expo
            ResponseEntity<String> response = restTemplate.postForEntity(EXPO_PUSH_URL, request, String.class);
            
            log.info("✅ Notificación enviada exitosamente. Response: {}", response.getBody());
        } catch (Exception e) {
            log.error("❌ Error enviando notificación push: {}", e.getMessage(), e);
            // No lanzamos excepción para no interrumpir el flujo principal
        }
    }

    /**
     * Envía una notificación genérica
     * 
     * @param expoPushToken Token de Expo Push del destinatario
     * @param title Título de la notificación
     * @param body Cuerpo de la notificación
     * @param data Datos adicionales (opcional)
     */
    public void sendNotification(String expoPushToken, String title, String body, Map<String, String> data) {
        if (expoPushToken == null || expoPushToken.isEmpty()) {
            log.warn("⚠️ No se puede enviar notificación: Push token vacío");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("to", expoPushToken);
            notification.put("sound", "default");
            notification.put("title", title);
            notification.put("body", body);
            notification.put("priority", "high");

            if (data != null && !data.isEmpty()) {
                notification.put("data", data);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(notification, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(EXPO_PUSH_URL, request, String.class);
            
            log.info("✅ Notificación enviada: {}", response.getBody());
        } catch (Exception e) {
            log.error("❌ Error enviando notificación", e);
        }
    }
    
    /**
     * Envía una notificación por email cuando se requiere aprobación de evento
     * (Para este MVP usaremos logs simulando el envío)
     */
    public void sendEventApprovalNotification(String partnerEmail, String partnerName, String creatorName, String eventTitle) {
        log.info("📧 SIMULACIÓN - Enviando notificación de aprobación de evento:");
        log.info("   Destinatario: {} ({})", partnerName, partnerEmail);
        log.info("   Evento: '{}' creado por {}", eventTitle, creatorName);
        log.info("   Mensaje: '¡Hola {}! {} ha creado el evento \"{}\" y necesita tu aprobación para confirmarlo en el calendario compartido.'", 
                partnerName, creatorName, eventTitle);
    }
    
    /**
     * Envía una notificación por email cuando un evento es confirmado
     */
    public void sendEventConfirmedNotification(String creatorEmail, String creatorName, String eventTitle) {
        log.info("📧 SIMULACIÓN - Enviando notificación de evento confirmado:");
        log.info("   Destinatario: {} ({})", creatorName, creatorEmail);
        log.info("   Evento: '{}'", eventTitle);
        log.info("   Mensaje: '¡Hola {}! Tu evento \"{}\" ha sido aprobado por tu pareja y está confirmado en el calendario.'", 
                creatorName, eventTitle);
    }
    
    /**
     * Envía una notificación por email cuando un evento es rechazado
     */
    public void sendEventRejectedNotification(String creatorEmail, String creatorName, String eventTitle) {
        log.info("📧 SIMULACIÓN - Enviando notificación de evento rechazado:");
        log.info("   Destinatario: {} ({})", creatorName, creatorEmail);
        log.info("   Evento: '{}'", eventTitle);
        log.info("   Mensaje: '¡Hola {}! Tu pareja ha rechazado el evento \"{}\" y no se añadirá al calendario.'", 
                creatorName, eventTitle);
    }
    
    /**
     * Envía una notificación por email cuando un evento es eliminado
     */
    public void sendEventDeletedNotification(String partnerEmail, String partnerName, String deletedBy, String eventTitle) {
        log.info("📧 SIMULACIÓN - Enviando notificación de evento eliminado:");
        log.info("   Destinatario: {} ({})", partnerName, partnerEmail);
        log.info("   Evento: '{}' eliminado por {}", eventTitle, deletedBy);
        log.info("   Mensaje: '¡Hola {}! {} ha eliminado el evento \"{}\" del calendario compartido.'", 
                partnerName, deletedBy, eventTitle);
    }
}
