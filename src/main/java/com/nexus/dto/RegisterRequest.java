package com.nexus.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    
    @NotBlank(message = "Ingresa tu correo electrónico.")
    @Email(message = "Ingresa un correo electrónico válido.")
    private String email;
    
    @NotBlank(message = "Escribe una contraseña.")
    @Size(min = 8, message = "Usa al menos 8 caracteres.")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-\\[\\]{};':\\\"\\\\|,.<>/?]).{8,}$",
        message = "Tu contraseña necesita mayúscula, minúscula, número y símbolo."
    )
    private String password;
    
    @NotBlank(message = "Escribe una contraseña.")
    private String confirmPassword;
    
    @NotBlank(message = "Escribe tu nombre.")
    @Size(min = 2, max = 255, message = "Tu nombre debe tener al menos 2 letras.")
    private String displayName;
    
    @NotBlank(message = "Escribe tu apodo.")
    @Size(max = 255, message = "Tu apodo es demasiado largo.")
    private String nickname;
    
    @NotNull(message = "Elige tu fecha de nacimiento.")
    @Past(message = "Elige una fecha de nacimiento que ya haya pasado.")
    private LocalDate birthDate;

    @AssertTrue(message = "Debes aceptar los términos y condiciones")
    private boolean termsAccepted;
}
