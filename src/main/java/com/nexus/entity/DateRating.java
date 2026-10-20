package com.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * CU42 / RF-41 - Calificacion de satisfaccion de una cita (tabla {@code date_ratings},
 * add_date_ratings.sql). Una por persona y por evento, sin edicion.
 *
 * <p>RN-37 - privada; RNF-11 - el puntaje va cifrado (AES-256-GCM), ver {@link
 * com.nexus.security.AesEncryptionService}.
 */
@Entity
@Table(name = "date_ratings")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DateRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "encrypted_score", nullable = false)
    private String encryptedScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
