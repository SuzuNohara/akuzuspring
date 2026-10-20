package com.nexus.controller;

import com.nexus.dto.PendingRatingResponse;
import com.nexus.dto.RateRequest;
import com.nexus.dto.RatingResponse;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.OwnershipGuard;
import com.nexus.service.RatingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CU42 / RF-41 - Calificar la satisfaccion de una cita. La ruta lleva {@code {userId}} para aplicar
 * {@link OwnershipGuard#requireSelf}; que la cita sea de su vinculo lo revisa el servicio.
 */
@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService service;

    /** GET /api/ratings/{userId}/pending */
    @GetMapping("/{userId}/pending")
    public ResponseEntity<List<PendingRatingResponse>> pending(
            @PathVariable Long userId, @AuthenticationPrincipal AuthenticatedUser currentUser) {
        OwnershipGuard.requireSelf(currentUser, userId);
        return ResponseEntity.ok(service.pending(userId));
    }

    /** POST /api/ratings/{userId}/events/{eventId} */
    @PostMapping("/{userId}/events/{eventId}")
    public ResponseEntity<RatingResponse> rate(
            @PathVariable Long userId,
            @PathVariable Long eventId,
            @Valid @RequestBody RateRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        OwnershipGuard.requireSelf(currentUser, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.rate(userId, eventId, request.score()));
    }
}
