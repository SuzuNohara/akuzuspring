package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAvatarResponse {
    
    private boolean success;
    
    private String message;
    
    /**
     * URL del avatar actualizado
     * Ejemplo: "/api/profile/1/avatar"
     */
    private String avatarUrl;
}
