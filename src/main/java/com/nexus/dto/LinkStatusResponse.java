package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkStatusResponse {
    private boolean hasActiveLink;
    private PartnerInfo partner;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartnerInfo {
        private Long userId;
        private String displayName;
        private String nickname;
        private String linkedAt;
        private String profilePhoto;
    }
}
