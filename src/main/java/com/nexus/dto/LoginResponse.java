package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
    
    // Token de sesion JWT (nexus-AUTH-01). Null en toda respuesta de fallo.
    private String token;

    // Caducidad del token, ISO-8601 UTC. Evita que el cliente tenga que
    // decodificar el JWT solo para leer exp.
    private Instant tokenExpiresAt;
    
    // Estado de verificación
    private boolean emailConfirmed;
    
    // Estado del cuestionario de preferencias
    private boolean questionnaireCompleted;
}
