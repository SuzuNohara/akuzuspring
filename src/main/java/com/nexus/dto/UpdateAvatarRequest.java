package com.nexus.dto;

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
public class UpdateAvatarRequest {
    
    /**
     * Imagen en formato Base64
     * Ejemplo: "data:image/jpeg;base64,/9j/4AAQSkZJRg..."
     */
    @NotBlank(message = "La imagen es requerida")
    @Pattern(
        regexp = "^data:image/(jpeg|jpg|png|webp);base64,[A-Za-z0-9+/=]+$",
        message = "Formato de imagen inválido. Solo se permiten JPEG, PNG o WEBP en Base64"
    )
    private String imageBase64;
}
