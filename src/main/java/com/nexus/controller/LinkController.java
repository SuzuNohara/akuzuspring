package com.nexus.controller;

import com.nexus.dto.LinkCodeResponse;
import com.nexus.dto.LinkStatusResponse;
import com.nexus.service.LinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/link")
@RequiredArgsConstructor
public class LinkController {
    
    private final LinkService linkService;
    
    /**
     * CU08 - Generar código de vínculo
     * POST /api/link/generate/{userId}
     */
    @PostMapping("/generate/{userId}")
    public ResponseEntity<LinkCodeResponse> generateLinkCode(@PathVariable Long userId) {
        LinkCodeResponse response = linkService.generateLinkCode(userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * CU09 - Establecer vínculo
     * POST /api/link/establish/{userId}
     */
    @PostMapping("/establish/{userId}")
    public ResponseEntity<LinkStatusResponse> establishLink(
            @PathVariable Long userId,
            @RequestBody EstablishLinkRequest request) {
        LinkStatusResponse response = linkService.establishLink(userId, request.getCode());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Obtener estado del vínculo del usuario
     * GET /api/link/status/{userId}
     */
    @GetMapping("/status/{userId}")
    public ResponseEntity<LinkStatusResponse> getLinkStatus(@PathVariable Long userId) {
        LinkStatusResponse response = linkService.getLinkStatus(userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * CU11 - Eliminar vínculo
     * DELETE /api/link/{userId}
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<UnlinkResponse> deleteLink(@PathVariable Long userId) {
        UnlinkResponse response = linkService.deleteLink(userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * DTO interno para la solicitud de establecer vínculo
     */
    @lombok.Data
    public static class EstablishLinkRequest {
        private String code;
    }
    
    /**
     * DTO para la respuesta de eliminación de vínculo
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UnlinkResponse {
        private boolean success;
        private String message;
        private String partnerName;
        private boolean notificationSent;
    }
}
