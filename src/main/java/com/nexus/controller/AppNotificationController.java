package com.nexus.controller;

import com.nexus.dto.planning.NotificationList;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.OwnershipGuard;
import com.nexus.service.AppNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** B9 - Avisos dentro de la app de cada persona. */
@RestController
@RequestMapping("/notifications/{userId}")
@RequiredArgsConstructor
public class AppNotificationController {

    private final AppNotificationService service;

    @GetMapping
    public ResponseEntity<NotificationList> list(@PathVariable Long userId, @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        return ResponseEntity.ok(service.list(userId));
    }

    @PostMapping("/read")
    public ResponseEntity<Void> read(@PathVariable Long userId, @AuthenticationPrincipal AuthenticatedUser me) {
        OwnershipGuard.requireSelf(me, userId);
        service.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
