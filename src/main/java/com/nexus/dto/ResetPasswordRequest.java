package com.nexus.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {
    
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    private String email;
    
    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "^\\d{6}$", message = "El código debe tener 6 dígitos")
    private String code;
    
    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-\\[\\]{};':\\\"\\\\|,.<>/?]).{8,}$",
        message = "La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un carácter especial"
    )
    private String newPassword;
    
    @NotBlank(message = "Debes confirmar la nueva contraseña")
    private String confirmPassword;
}
