package com.nexus.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyEmailResponse {
    
    private boolean success;
    private String message;
    private Long userId;
    private String email;
    private String displayName;
    private String nickname;
    private boolean emailConfirmed;

    // Figma H4: al verificar el correo se entra directo, con la misma sesion que da el login.
    // Null en verify-reset-code y en toda respuesta de fallo.
    private String linkCode;
    private String token;
    private Instant tokenExpiresAt;
    private boolean questionnaireCompleted;
}
