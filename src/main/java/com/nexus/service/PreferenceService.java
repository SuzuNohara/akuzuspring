package com.nexus.service;

import com.nexus.dto.*;
import com.nexus.entity.*;
import com.nexus.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreferenceService {
    
    private final PreferenceCategoryRepository categoryRepository;
    private final PreferenceRepository preferenceRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;
    
    /**
     * Obtener todas las categorías con sus preferencias
     */
    public List<PreferenceCategoryDTO> getAllCategories() {
        return categoryRepository.findAllActive().stream()
                .map(this::toCategoryDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Obtener preferencias de una categoría específica
     */
    public List<PreferenceDTO> getPreferencesByCategory(Long categoryId) {
        return preferenceRepository.findByCategoryId(categoryId).stream()
                .map(this::toPreferenceDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Verificar si el usuario ya completó el cuestionario
     * RN-14: Debe tener al menos una preferencia en cada una de las 7 dimensiones
     */
    public boolean hasCompletedQuestionnaire(Long userId) {
        // Obtener todas las categorías activas
        List<PreferenceCategory> allCategories = categoryRepository.findAllActive();
        
        // Verificar que existen las 7 dimensiones
        if (allCategories.size() < 7) {
            log.warn("⚠️ No hay 7 dimensiones configuradas en el sistema");
            return false;
        }
        
        // Obtener IDs de categorías con preferencias del usuario
        List<Long> userCategoryIds = userPreferenceRepository.findByUserId(userId).stream()
                .map(up -> up.getPreference().getCategory().getId())
                .distinct()
                .collect(Collectors.toList());
        
        // Verificar que el usuario tenga preferencias en todas las categorías
        boolean hasAllDimensions = allCategories.stream()
                .allMatch(cat -> userCategoryIds.contains(cat.getId()));
        
        log.info("📋 Usuario {} - Cuestionario completo: {} ({}/{} dimensiones)", 
                userId, hasAllDimensions, userCategoryIds.size(), allCategories.size());
        
        return hasAllDimensions;
    }
    
    /**
     * Obtener estado del cuestionario del usuario
     */
    public QuestionnaireStatusDTO getQuestionnaireStatus(Long userId) {
        boolean completed = hasCompletedQuestionnaire(userId);
        List<UserPreferenceDTO> userPreferences = getUserPreferences(userId);
        
        return QuestionnaireStatusDTO.builder()
                .completed(completed)
                .totalPreferences(preferenceRepository.findAllActive().size())
                .completedPreferences(userPreferences.size())
                .userPreferences(userPreferences)
                .build();
    }
    
    /**
     * Obtener preferencias del usuario
     */
    public List<UserPreferenceDTO> getUserPreferences(Long userId) {
        return userPreferenceRepository.findByUserId(userId).stream()
                .map(this::toUserPreferenceDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Guardar preferencias del usuario
     */
    @Transactional
    public void saveUserPreferences(Long userId, List<UserPreferenceRequest> preferences) {
        log.info("💾 Guardando {} preferencias para usuario {}", preferences.size(), userId);
        
        // Verificar que el usuario existe primero
        if (!userRepository.existsById(userId)) {
            log.error("❌ Usuario no encontrado: {}", userId);
            throw new RuntimeException("Usuario no encontrado con ID: " + userId);
        }
        
        User user = userRepository.findById(userId).get();
        
        // Guardar cada preferencia
        for (UserPreferenceRequest req : preferences) {
            Preference preference = preferenceRepository.findById(req.getPreferenceId())
                    .orElseThrow(() -> new RuntimeException("Preferencia no encontrada: " + req.getPreferenceId()));
            
            UserPreferenceId id = new UserPreferenceId(userId, req.getPreferenceId());
            
            // Buscar si ya existe
            UserPreference userPref = userPreferenceRepository.findById(id)
                    .orElse(UserPreference.builder()
                            .user(user)
                            .preference(preference)
                            .build());
            
            userPref.setLevel(req.getLevel());
            userPref.setNotes(req.getNotes());
            
            userPreferenceRepository.save(userPref);
        }
        
        log.info("✅ Preferencias guardadas exitosamente para usuario {}", userId);
    }
    
    // Métodos de conversión DTO
    private PreferenceCategoryDTO toCategoryDTO(PreferenceCategory category) {
        return PreferenceCategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .preferences(category.getPreferences().stream()
                        .filter(p -> p.getDeletedAt() == null)
                        .map(this::toPreferenceDTO)
                        .collect(Collectors.toList()))
                .build();
    }
    
    private PreferenceDTO toPreferenceDTO(Preference preference) {
        return PreferenceDTO.builder()
                .id(preference.getId())
                .name(preference.getName())
                .description(preference.getDescription())
                .categoryId(preference.getCategory().getId())
                .categoryName(preference.getCategory().getName())
                .build();
    }
    
    private UserPreferenceDTO toUserPreferenceDTO(UserPreference userPreference) {
        return UserPreferenceDTO.builder()
                .preferenceId(userPreference.getPreference().getId())
                .preferenceName(userPreference.getPreference().getName())
                .categoryName(userPreference.getPreference().getCategory().getName())
                .level(userPreference.getLevel())
                .notes(userPreference.getNotes())
                .build();
    }
}
