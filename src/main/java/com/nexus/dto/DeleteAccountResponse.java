package com.nexus.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeleteAccountResponse {
    private String message;
    private String deletedEmail;
}
