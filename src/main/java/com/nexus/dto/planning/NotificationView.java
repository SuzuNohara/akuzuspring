package com.nexus.dto.planning;

import java.time.Instant;

/** B9 - Un aviso dentro de la app. */
public record NotificationView(Long id, String type, String title, String body, Instant createdAt, boolean read) {}
