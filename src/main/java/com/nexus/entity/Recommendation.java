package com.nexus.entity;

import com.nexus.model.ReasonSource;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * RF-33 / RN-39 - Una actividad mostrada dentro de una solicitud. Una actividad no se repite en
 * la misma solicitud (UNIQUE request_id + activity_id), ni siquiera despues de descartarla.
 */
@Entity
@Table(name = "recommendations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private RecommendationRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    /** Lugar elegido para una actividad en la ciudad; {@code null} en casa. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    /** Busqueda en la que se mostro (1 a 3). */
    @Column(nullable = false)
    private Integer round;

    /** Lugar dentro de su busqueda (1 a 5). */
    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_source", nullable = false)
    private ReasonSource reasonSource;

    @Builder.Default
    @Column(nullable = false)
    private Boolean discarded = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean chosen = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
