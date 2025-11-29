package com.nexus.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {
    
    @NotBlank(message = "La contraseña es requerida")
    private String password;
}
