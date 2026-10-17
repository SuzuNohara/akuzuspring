package com.nexus.controller;

import com.nexus.dto.planning.MessageRequest;
import com.nexus.dto.planning.MessageView;
import com.nexus.dto.planning.PlanningFilters;
import com.nexus.dto.planning.PlanningView;
import com.nexus.dto.planning.ReadyRequest;
import com.nexus.dto.planning.VoteRequest;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.OwnershipGuard;
import com.nexus.service.planning.PlanningService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF-38 / RF-39 - Planeacion en pareja. La ruta lleva {@code {userId}} para
 * {@link OwnershipGuard#requireSelf}; que la sesion sea de esa persona lo revisa el servicio.
 */
@RestController
@RequestMapping("/planning/{userId}")
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningService service;

    @PostMapping("/invite")
    public ResponseEntity<PlanningView> invite(@PathVariable Long userId, @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.invite(userId));
    }

    /** La planeacion abierta (o recien terminada); 204 si no hay. */
    @GetMapping("/current")
    public ResponseEntity<PlanningView> current(@PathVariable Long userId, @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return service.current(userId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{sessionId}/accept")
    public ResponseEntity<PlanningView> accept(@PathVariable Long userId, @PathVariable Long sessionId,
                                               @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.accept(userId, sessionId));
    }

    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<PlanningView> cancel(@PathVariable Long userId, @PathVariable Long sessionId,
                                               @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.cancel(userId, sessionId));
    }

    @PutMapping("/{sessionId}/filters")
    public ResponseEntity<PlanningView> filters(@PathVariable Long userId, @PathVariable Long sessionId,
                                                @Valid @RequestBody PlanningFilters filters,
                                                @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.updateFilters(userId, sessionId, filters));
    }

    @PostMapping("/{sessionId}/ready")
    public ResponseEntity<PlanningView> ready(@PathVariable Long userId, @PathVariable Long sessionId,
                                              @RequestBody(required = false) ReadyRequest location,
                                              @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.ready(userId, sessionId, location));
    }

    @PostMapping("/{sessionId}/unready")
    public ResponseEntity<PlanningView> unready(@PathVariable Long userId, @PathVariable Long sessionId,
                                                @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.unready(userId, sessionId));
    }

    @PostMapping("/{sessionId}/adjust")
    public ResponseEntity<PlanningView> adjust(@PathVariable Long userId, @PathVariable Long sessionId,
                                               @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.adjust(userId, sessionId));
    }

    @PostMapping("/{sessionId}/options/more")
    public ResponseEntity<PlanningView> more(@PathVariable Long userId, @PathVariable Long sessionId,
                                             @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.more(userId, sessionId));
    }

    @PostMapping("/{sessionId}/options/{recommendationId}/vote")
    public ResponseEntity<PlanningView> vote(@PathVariable Long userId, @PathVariable Long sessionId,
                                             @PathVariable Long recommendationId, @Valid @RequestBody VoteRequest body,
                                             @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.vote(userId, sessionId, recommendationId, body.vote()));
    }

    @PostMapping("/{sessionId}/options/{recommendationId}/propose")
    public ResponseEntity<PlanningView> propose(@PathVariable Long userId, @PathVariable Long sessionId,
                                                @PathVariable Long recommendationId,
                                                @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.propose(userId, sessionId, recommendationId));
    }

    @PostMapping("/{sessionId}/proposal/withdraw")
    public ResponseEntity<PlanningView> withdraw(@PathVariable Long userId, @PathVariable Long sessionId,
                                                 @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.withdraw(userId, sessionId));
    }

    @PostMapping("/{sessionId}/proposal/accept")
    public ResponseEntity<PlanningView> acceptProposal(@PathVariable Long userId, @PathVariable Long sessionId,
                                                       @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.acceptProposal(userId, sessionId));
    }

    @PostMapping("/{sessionId}/proposal/reject")
    public ResponseEntity<PlanningView> rejectProposal(@PathVariable Long userId, @PathVariable Long sessionId,
                                                       @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.rejectProposal(userId, sessionId));
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<MessageView>> messages(@PathVariable Long userId, @PathVariable Long sessionId,
                                                      @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.messages(userId, sessionId));
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<List<MessageView>> send(@PathVariable Long userId, @PathVariable Long sessionId,
                                                  @Valid @RequestBody MessageRequest body,
                                                  @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.sendMessage(userId, sessionId, body.body()));
    }
}
