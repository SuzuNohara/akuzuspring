package com.nexus.controller;

import com.nexus.dto.*;
import com.nexus.service.PreferenceService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/preferences")
@RequiredArgsConstructor
@Slf4j
public class PreferenceController {
    
    private final PreferenceService preferenceService;
    
    /**
     * Obtener todas las categorías con sus preferencias
     * GET /api/preferences/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<PreferenceCategoryDTO>> getAllCategories() {
        log.info("📋 Obteniendo todas las categorías de preferencias");
        List<PreferenceCategoryDTO> categories = preferenceService.getAllCategories();
        return ResponseEntity.ok(categories);
    }
    
    /**
     * Obtener preferencias de una categoría específica
     * GET /api/preferences/category/{categoryId}
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<PreferenceDTO>> getPreferencesByCategory(@PathVariable Long categoryId) {
        log.info("📋 Obteniendo preferencias de categoría {}", categoryId);
        List<PreferenceDTO> preferences = preferenceService.getPreferencesByCategory(categoryId);
        return ResponseEntity.ok(preferences);
    }
    
    /**
     * Verificar estado del cuestionario del usuario
     * GET /api/preferences/status/{userId}
     */
    @GetMapping("/status/{userId}")
    public ResponseEntity<QuestionnaireStatusDTO> getQuestionnaireStatus(@PathVariable Long userId) {
        log.info("🔍 Verificando estado del cuestionario para usuario {}", userId);
        QuestionnaireStatusDTO status = preferenceService.getQuestionnaireStatus(userId);
        return ResponseEntity.ok(status);
    }
    
    /**
     * Obtener preferencias del usuario
     * GET /api/preferences/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserPreferenceDTO>> getUserPreferences(@PathVariable Long userId) {
        log.info("👤 Obteniendo preferencias del usuario {}", userId);
        List<UserPreferenceDTO> preferences = preferenceService.getUserPreferences(userId);
        return ResponseEntity.ok(preferences);
    }
    
    /**
     * Guardar preferencias del usuario
     * POST /api/preferences/user/{userId}
     */
    @PostMapping("/user/{userId}")
    public ResponseEntity<MessageResponse> saveUserPreferences(
            @PathVariable Long userId,
            @RequestBody SavePreferencesRequest request) {
        
        log.info("💾 Guardando preferencias para usuario {}", userId);
        preferenceService.saveUserPreferences(userId, request.getPreferences());
        
        return ResponseEntity.ok(MessageResponse.builder()
                .success(true)
                .message("Preferencias guardadas exitosamente")
                .build());
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private boolean success;
        private String message;
    }
}
