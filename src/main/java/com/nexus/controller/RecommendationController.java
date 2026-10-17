package com.nexus.controller;

import com.nexus.dto.BoroughResponse;
import com.nexus.dto.CreateEventResponse;
import com.nexus.dto.DiscardResponse;
import com.nexus.dto.RecommendationBatch;
import com.nexus.dto.RecommendationParams;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.OwnershipGuard;
import com.nexus.service.recommendation.RecommendationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF-33 / RF-37 / RF-40 - Recomendaciones en la modalidad individual. La ruta lleva
 * {@code {userId}} para aplicar {@link OwnershipGuard#requireSelf} (nexus-SEC-04); la pertenencia de
 * cada solicitud u opcion la revisa el servicio.
 */
@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {

    private final RecommendationService service;

    /** GET /api/recommendations/boroughs */
    @GetMapping("/boroughs")
    public ResponseEntity<List<BoroughResponse>> boroughs() {
        return ResponseEntity.ok(service.boroughs());
    }

    /** POST /api/recommendations/{userId} */
    @PostMapping("/{userId}")
    public ResponseEntity<RecommendationBatch> request(
            @PathVariable Long userId,
            @Valid @RequestBody RecommendationParams params,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        OwnershipGuard.requireSelf(currentUser, userId);
        log.info("Usuario {} pide recomendaciones (zona {}, alcance {})", userId, params.zoneType(), params.scope());
        return ResponseEntity.ok(service.request(userId, params));
    }

    /** POST /api/recommendations/{userId}/requests/{requestId}/more */
    @PostMapping("/{userId}/requests/{requestId}/more")
    public ResponseEntity<RecommendationBatch> more(
            @PathVariable Long userId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        OwnershipGuard.requireSelf(currentUser, userId);
        return ResponseEntity.ok(service.more(userId, requestId));
    }

    /** POST /api/recommendations/{userId}/items/{recommendationId}/discard */
    @PostMapping("/{userId}/items/{recommendationId}/discard")
    public ResponseEntity<DiscardResponse> discard(
            @PathVariable Long userId,
            @PathVariable Long recommendationId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        OwnershipGuard.requireSelf(currentUser, userId);
        return ResponseEntity.ok(service.discard(userId, recommendationId));
    }

    /** POST /api/recommendations/{userId}/items/{recommendationId}/invite */
    @PostMapping("/{userId}/items/{recommendationId}/invite")
    public ResponseEntity<CreateEventResponse> invite(
            @PathVariable Long userId,
            @PathVariable Long recommendationId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        OwnershipGuard.requireSelf(currentUser, userId);
        return ResponseEntity.ok(service.invite(userId, recommendationId));
    }
}
