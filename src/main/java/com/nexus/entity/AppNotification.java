package com.nexus.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/** B9 - Aviso dentro de la app mientras no haya notificaciones push. */
@Entity
@Table(name = "app_notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(nullable = false, length = 24)
    private String type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 200)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;
}
