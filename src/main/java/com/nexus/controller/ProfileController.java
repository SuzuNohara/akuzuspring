package com.nexus.controller;

import com.nexus.dto.DeleteAccountRequest;
import com.nexus.dto.DeleteAccountResponse;
import com.nexus.dto.UpdateAvatarRequest;
import com.nexus.dto.UpdateAvatarResponse;
import com.nexus.dto.UpdateProfileRequest;
import com.nexus.dto.UpdateProfileResponse;
import com.nexus.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {
    
    private final UserService userService;
    
    /**
     * Actualizar perfil de usuario (CU05)
     * PUT /api/profile/{userId}
     */
    @PutMapping("/{userId}")
    public ResponseEntity<UpdateProfileResponse> updateProfile(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        
        log.info("Solicitud de actualización de perfil para usuario ID: {}", userId);
        
        UpdateProfileResponse response = userService.updateProfile(userId, request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Actualizar avatar de usuario
     * PUT /api/profile/{userId}/avatar
     */
    @PutMapping("/{userId}/avatar")
    public ResponseEntity<UpdateAvatarResponse> updateAvatar(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateAvatarRequest request) {
        
        log.info("Solicitud de actualización de avatar para usuario ID: {}", userId);
        
        UpdateAvatarResponse response = userService.updateAvatar(userId, request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Obtener avatar de usuario
     * GET /api/profile/{userId}/avatar
     */
    @GetMapping("/{userId}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable Long userId) {
        
        log.info("Solicitud de obtención de avatar para usuario ID: {}", userId);
        
        var avatarData = userService.getAvatar(userId);
        
        if (avatarData == null || avatarData.getBytes() == null) {
            return ResponseEntity.notFound().build();
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(avatarData.getMimeType()));
        headers.setCacheControl("max-age=86400"); // Cache por 24 horas
        
        return new ResponseEntity<>(avatarData.getBytes(), headers, HttpStatus.OK);
    }
    
    /**
     * Eliminar avatar de usuario
     * DELETE /api/profile/{userId}/avatar
     */
    @DeleteMapping("/{userId}/avatar")
    public ResponseEntity<UpdateAvatarResponse> deleteAvatar(@PathVariable Long userId) {
        
        log.info("Solicitud de eliminación de avatar para usuario ID: {}", userId);
        
        UpdateAvatarResponse response = userService.deleteAvatar(userId);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Eliminar cuenta de usuario (CU07)
     * DELETE /api/profile/{userId}
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<DeleteAccountResponse> deleteAccount(
            @PathVariable Long userId,
            @Valid @RequestBody DeleteAccountRequest request) {
        
        log.info("Solicitud de eliminación de cuenta para usuario ID: {}", userId);
        
        DeleteAccountResponse response = userService.deleteAccount(userId, request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Registrar token FCM para notificaciones push
     * POST /api/profile/{userId}/fcm-token
     */
    @PostMapping("/{userId}/fcm-token")
    public ResponseEntity<Void> registerFCMToken(
            @PathVariable Long userId,
            @RequestBody java.util.Map<String, String> request) {
        
        log.info("Registrando token FCM para usuario ID: {}", userId);
        
        String fcmToken = request.get("fcmToken");
        if (fcmToken == null || fcmToken.isEmpty()) {
            log.warn("Token FCM vacío para usuario ID: {}", userId);
            return ResponseEntity.badRequest().build();
        }
        
        userService.updateFCMToken(userId, fcmToken);
        
        return ResponseEntity.ok().build();
    }
}
