package com.nexus.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    
    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String displayName;
    
    @Size(max = 50, message = "El apodo no puede tener más de 50 caracteres")
    private String nickname;
    
    @Email(message = "El formato del correo electrónico no es válido")
    @NotBlank(message = "El correo electrónico es obligatorio")
    private String email;
    
    @Past(message = "La fecha de nacimiento debe ser una fecha pasada")
    @NotNull(message = "La fecha de nacimiento es obligatoria")
    private LocalDate birthDate;
}
