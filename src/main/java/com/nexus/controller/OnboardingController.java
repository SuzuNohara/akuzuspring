package com.nexus.controller;

import com.nexus.dto.OnboardingProgressResponse;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.OwnershipGuard;
import com.nexus.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** [primeros-pasos] Avance de la tarjeta "Primeros pasos": cada quien pide solo el suyo. */
@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    /** GET /api/onboarding/{userId}/progress */
    @GetMapping("/{userId}/progress")
    public ResponseEntity<OnboardingProgressResponse> progress(
            @PathVariable Long userId, @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(onboardingService.progressFor(userId));
    }
}
