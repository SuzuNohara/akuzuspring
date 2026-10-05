package com.nexus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * RF-31/RF-32 - Registro e historial de estado emocional.
 * RN-28 - Privado: no visible para la pareja vinculada.
 * RNF-11 - encryptedPayload guarda valencia/activacion/etiqueta cifrados (AES-256-GCM), nunca en
 * claro. Ver {@link com.nexus.security.AesEncryptionService}.
 */
@Entity
@Table(name = "emotion_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmotionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "encrypted_payload", nullable = false, length = 512)
    private String encryptedPayload;

    @CreationTimestamp
    @Column(name = "logged_at", nullable = false, updatable = false)
    private Instant loggedAt;
}
