package com.nexus.dto.planning;

import java.time.Instant;

/** B6 - Un mensaje del chat de la planeacion. */
public record MessageView(Long id, boolean mine, String senderName, String body, Instant createdAt) {}
