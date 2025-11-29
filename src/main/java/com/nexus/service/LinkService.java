package com.nexus.service;

import com.nexus.controller.LinkController;
import com.nexus.dto.LinkCodeResponse;
import com.nexus.dto.LinkStatusResponse;
import com.nexus.entity.Link;
import com.nexus.entity.LinkCode;
import com.nexus.entity.User;
import com.nexus.exception.BadRequestException;
import com.nexus.exception.ResourceNotFoundException;
import com.nexus.repository.LinkCodeRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.UserRepository;
import com.nexus.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkService {
    
    private final LinkRepository linkRepository;
    private final LinkCodeRepository linkCodeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EventRepository eventRepository;
    
    private static final int CODE_LENGTH = 6;
    private static final int CODE_VALIDITY_MINUTES = 15; // RN-09
    private static final String CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Sin caracteres confusos
    
    /**
     * CU08 - Generar código de vínculo
     * RN-08: Usuario no debe tener un vínculo activo
     * RN-09: Código expira en 15 minutos y solo puede usarse una vez
     */
    @Transactional
    public LinkCodeResponse generateLinkCode(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        
        // RN-08: Verificar que el usuario no tenga un vínculo activo
        if (linkRepository.existsActiveLinkByUserId(userId)) {
            throw new BadRequestException("Ya tienes un vínculo activo. Debes desvincularte antes de generar un nuevo código.");
        }
        
        // Verificar si ya tiene un código activo y válido (no expirado)
        Optional<LinkCode> existingCode = linkCodeRepository.findActiveCodeByUserId(userId);
        if (existingCode.isPresent()) {
            LinkCode code = existingCode.get();
            // Solo devolver el código si aún no ha expirado
            if (!code.isExpired()) {
                return LinkCodeResponse.builder()
                        .code(code.getCode())
                        .expiresAt(code.getExpiresAt())
                        .validityMinutes(CODE_VALIDITY_MINUTES)
                        .message("Ya tienes un código activo")
                        .build();
            }
            // Si el código está expirado, eliminarlo
            linkCodeRepository.delete(code);
        }
        
        // Generar un código único
        String code = generateUniqueCode();
        
        // RN-09: Crear código con validez de 15 minutos
        Instant expiresAt = Instant.now().plus(CODE_VALIDITY_MINUTES, ChronoUnit.MINUTES);
        
        LinkCode linkCode = LinkCode.builder()
                .code(code)
                .generatedByUser(user)
                .isUsed(false)
                .expiresAt(expiresAt)
                .build();
        
        linkCodeRepository.save(linkCode);
        
        return LinkCodeResponse.builder()
                .code(code)
                .expiresAt(expiresAt)
                .validityMinutes(CODE_VALIDITY_MINUTES)
                .message("Código generado exitosamente")
                .build();
    }
    
    /**
     * CU09 - Establecer vínculo
     * RN-08: Usuario no debe tener un vínculo activo
     * RN-09: Código debe estar vigente (15 minutos) y no haber sido usado
     * RN-10: Usuario no puede usar su propio código
     */
    @Transactional
    public LinkStatusResponse establishLink(Long userId, String code) {
        // Buscar el usuario que intenta usar el código
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        
        // RN-08: Verificar que el usuario no tenga un vínculo activo
        if (linkRepository.existsActiveLinkByUserId(userId)) {
            throw new BadRequestException("Ya tienes un vínculo activo. Debes desvincularte primero.");
        }
        
        // FA02: Verificar que el código exista
        LinkCode linkCode = linkCodeRepository.findByCode(code)
                .orElseThrow(() -> new BadRequestException("Código inválido. Verifica e intenta nuevamente."));
        
        // RN-09: Verificar que el código no haya expirado
        if (linkCode.isExpired()) {
            throw new BadRequestException("El código ha expirado. Solicita un nuevo código a tu pareja.");
        }
        
        // RN-09: Verificar que el código no haya sido usado
        if (linkCode.getIsUsed()) {
            throw new BadRequestException("Este código ya fue utilizado. Solicita un nuevo código a tu pareja.");
        }
        
        // RN-10: Verificar que no sea el mismo usuario (no puede usar su propio código)
        if (linkCode.getGeneratedByUser().getId().equals(userId)) {
            throw new BadRequestException("No puedes usar tu propio código de vínculo.");
        }
        
        // Verificar que el generador del código no tenga un vínculo activo
        if (linkRepository.existsActiveLinkByUserId(linkCode.getGeneratedByUser().getId())) {
            throw new BadRequestException("Tu pareja ya tiene un vínculo activo. Debe desvincularse primero.");
        }
        
        // Establecer el vínculo
        Link link = Link.builder()
                .initiatorUser(linkCode.getGeneratedByUser()) // El que generó el código
                .partnerUser(user) // El que usa el código
                .codeInUse(code)
                .isActive(true)
                .startedAt(Instant.now())
                .build();
        
        linkRepository.save(link);
        
        // Marcar el código como usado
        linkCode.setIsUsed(true);
        linkCode.setUsedByUserId(user.getId());
        linkCode.setUsedAt(Instant.now());
        linkCodeRepository.save(linkCode);
        
        // Enviar notificación push al generador del código
        User codeGenerator = linkCode.getGeneratedByUser();
        log.info("💌 Intentando enviar notificación a usuario ID: {}", codeGenerator.getId());
        log.info("💌 FCM Token del generador: {}", codeGenerator.getFcmToken() != null ? 
            codeGenerator.getFcmToken().substring(0, Math.min(20, codeGenerator.getFcmToken().length())) + "..." : "NULL");
        
        if (codeGenerator.getFcmToken() != null && !codeGenerator.getFcmToken().isEmpty()) {
            String partnerName = user.getDisplayName() != null ? user.getDisplayName() : user.getNickname();
            log.info("💌 Enviando notificación con nombre de pareja: {}", partnerName);
            notificationService.sendLinkEstablishedNotification(codeGenerator.getFcmToken(), partnerName);
        } else {
            log.warn("⚠️ Usuario ID: {} no tiene token FCM registrado. No se puede enviar notificación.", codeGenerator.getId());
        }
        
        // Retornar información del vínculo establecido
        User partner = linkCode.getGeneratedByUser();
        String profilePhotoUrl = (partner.getProfile() != null && partner.getProfile().getAvatarBytes() != null)
                ? "http://192.168.1.95:8080/api/profile/" + partner.getId() + "/avatar"
                : null;
        
        LinkStatusResponse.PartnerInfo partnerInfo = LinkStatusResponse.PartnerInfo.builder()
                .userId(partner.getId())
                .displayName(partner.getDisplayName())
                .nickname(partner.getNickname())
                .linkedAt(link.getStartedAt().toString())
                .profilePhoto(profilePhotoUrl)
                .build();
        
        return LinkStatusResponse.builder()
                .hasActiveLink(true)
                .partner(partnerInfo)
                .build();
    }
    
    /**
     * Obtener el estado del vínculo del usuario
     */
    @Transactional(readOnly = true)
    public LinkStatusResponse getLinkStatus(Long userId) {
        Optional<Link> activeLink = linkRepository.findActiveLinkByUserId(userId);
        
        if (activeLink.isEmpty()) {
            return LinkStatusResponse.builder()
                    .hasActiveLink(false)
                    .build();
        }
        
        Link link = activeLink.get();
        User partner = link.getInitiatorUser().getId().equals(userId) ? link.getPartnerUser() : link.getInitiatorUser();
        
        String profilePhotoUrl = (partner.getProfile() != null && partner.getProfile().getAvatarBytes() != null)
                ? "http://192.168.1.95:8080/api/profile/" + partner.getId() + "/avatar"
                : null;
        
        LinkStatusResponse.PartnerInfo partnerInfo = LinkStatusResponse.PartnerInfo.builder()
                .userId(partner.getId())
                .displayName(partner.getDisplayName())
                .nickname(partner.getNickname())
                .linkedAt(link.getStartedAt().toString())
                .profilePhoto(profilePhotoUrl)
                .build();
        
        return LinkStatusResponse.builder()
                .hasActiveLink(true)
                .partner(partnerInfo)
                .build();
    }
    
    /**
     * Generar un código único que no exista en la base de datos
     */
    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        int maxAttempts = 10;
        
        do {
            code = generateRandomCode();
            attempts++;
            
            if (attempts >= maxAttempts) {
                throw new RuntimeException("No se pudo generar un código único después de " + maxAttempts + " intentos");
            }
        } while (linkCodeRepository.existsByCode(code));
        
        return code;
    }
    
    /**
     * Generar un código aleatorio
     */
    private String generateRandomCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = random.nextInt(CODE_CHARACTERS.length());
            code.append(CODE_CHARACTERS.charAt(index));
        }
        
        return code.toString();
    }
    
    /**
     * CU11 - Eliminación de vínculo
     * RN-08: Usuario debe tener un vínculo activo
     * RN-11: Se eliminan todos los datos compartidos
     * RN-12: Se envía notificación al otro usuario del vínculo
     */
    @Transactional
    public LinkController.UnlinkResponse deleteLink(Long userId) {
        log.info("🔗 Iniciando eliminación de vínculo para usuario: {}", userId);
        
        // Buscar el usuario
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        // RN-08: Verificar que el usuario tenga un vínculo activo
        Optional<Link> activeLinkOpt = linkRepository.findActiveLinkByUserId(userId);
        if (activeLinkOpt.isEmpty()) {
            log.warn("⚠️ Usuario {} no tiene un vínculo activo", userId);
            return LinkController.UnlinkResponse.builder()
                    .success(false)
                    .message("No tienes un vínculo activo para eliminar")
                    .build();
        }
        
        Link link = activeLinkOpt.get();
        
        // Identificar al partner (el otro usuario del vínculo)
        User partner = link.getInitiatorUser().getId().equals(userId) 
                ? link.getPartnerUser() 
                : link.getInitiatorUser();
        
        String partnerName = partner.getDisplayName();
        String partnerPushToken = partner.getFcmToken();
        
        log.info("🔗 Vínculo encontrado. Partner: {} (ID: {})", partnerName, partner.getId());
        
        boolean notificationSent = false;
        
        try {
            // RN-11: Eliminar todos los datos compartidos
            log.info("🗑️ Eliminando datos compartidos del vínculo");
            
            // Eliminar todos los eventos asociados al vínculo
            var events = eventRepository.findByLinkIdAndNotDeleted(link.getId());
            if (!events.isEmpty()) {
                log.info("🗑️ Eliminando {} eventos del vínculo", events.size());
                eventRepository.deleteAll(events);
                log.info("✅ Eventos eliminados");
            }
            
            // RN-12: Enviar notificación al partner ANTES de eliminar el vínculo
            if (partnerPushToken != null && !partnerPushToken.isEmpty()) {
                log.info("🔔 Enviando notificación de eliminación de vínculo a {}", partnerName);
                log.info("🔔 Token FCM: {}...", partnerPushToken.substring(0, Math.min(30, partnerPushToken.length())));
                
                Map<String, String> notificationData = new HashMap<>();
                notificationData.put("type", "LINK_DELETED");
                notificationData.put("partnerName", user.getDisplayName());
                
                notificationService.sendNotification(
                        partnerPushToken,
                        "Vínculo eliminado",
                        user.getDisplayName() + " ha terminado la conexión contigo",
                        notificationData
                );
                notificationSent = true;
            } else {
                log.warn("⚠️ No se pudo enviar notificación: Partner no tiene Push Token");
                log.warn("⚠️ Partner ID: {}, Token: {}", partner.getId(), partnerPushToken == null ? "null" : "empty");
                log.info("ℹ️ El partner debe abrir la app en un dispositivo físico para recibir notificaciones");
            }
            
            // Eliminar físicamente el vínculo (evita problemas con el constraint uk_couple_active)
            linkRepository.delete(link);
            
            log.info("✅ Vínculo eliminado exitosamente");
            
            log.info("✅ Vínculo eliminado exitosamente para usuario {}", userId);
            
            return LinkController.UnlinkResponse.builder()
                    .success(true)
                    .message("Vínculo eliminado exitosamente")
                    .partnerName(partnerName)
                    .notificationSent(notificationSent)
                    .build();
                    
        } catch (Exception e) {
            log.error("❌ Error eliminando vínculo: {}", e.getMessage(), e);
            throw new RuntimeException("Error al eliminar el vínculo: " + e.getMessage());
        }
    }
}
