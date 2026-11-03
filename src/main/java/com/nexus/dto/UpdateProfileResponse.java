package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileResponse {
    
    private Long userId;
    private String email;
    private String displayName;
    private String nickname;
    private LocalDate birthDate;
    private Boolean emailConfirmed;
    private Boolean emailChanged;
    private String message;
    private Instant updatedAt;
}
