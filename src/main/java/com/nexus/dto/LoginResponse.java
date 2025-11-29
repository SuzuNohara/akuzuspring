package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    private boolean success;
    private String message;
    
    // Información del usuario
    private Long userId;
    private String email;
    private String displayName;
    private String nickname;
    private String linkCode;
    
    // Token de sesión (si implementas JWT en el futuro)
    private String token;
    
    // Estado de verificación
    private boolean emailConfirmed;
    
    // Estado del cuestionario de preferencias
    private boolean questionnaireCompleted;
}
