package com.nexus.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/** RF-39 / RN-34 - Mensaje del chat de una planeacion. Se borra al cerrar la sesion. */
@Entity
@Table(name = "planning_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanningMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, length = 500)
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
