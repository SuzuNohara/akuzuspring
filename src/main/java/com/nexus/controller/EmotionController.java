package com.nexus.controller;

import com.nexus.dto.EmotionLogResponse;
import com.nexus.dto.LogEmotionRequest;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.OwnershipGuard;
import com.nexus.service.EmotionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * RF-31/RF-32 - Registro e historial de estado emocional.
 *
 * <p>RN-28: privado. A diferencia del avatar de perfil, ni siquiera la pareja vinculada puede leer
 * esto -- por eso {@link OwnershipGuard#requireSelf} en ambas rutas, no
 * {@code LinkMembershipGuard}.
 */
@RestController
@RequestMapping("/emotions")
@RequiredArgsConstructor
@Slf4j
public class EmotionController {

    private final EmotionService emotionService;

    /**
     * POST /api/emotions/{userId}
     */
    @PostMapping("/{userId}")
    public ResponseEntity<EmotionLogResponse> logEmotion(
            @PathVariable Long userId,
            @Valid @RequestBody LogEmotionRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        OwnershipGuard.requireSelf(currentUser, userId);

        log.info("Registrando estado emocional para usuario {}", userId);
        EmotionLogResponse response = emotionService.logEmotion(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/emotions/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<EmotionLogResponse>> getHistory(
            @PathVariable Long userId, @AuthenticationPrincipal AuthenticatedUser currentUser) {
        OwnershipGuard.requireSelf(currentUser, userId);

        log.info("Obteniendo historial emocional de usuario {}", userId);
        return ResponseEntity.ok(emotionService.getHistory(userId));
    }
}
