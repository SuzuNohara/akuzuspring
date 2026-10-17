package com.nexus.entity;

import com.nexus.model.RecommendationMode;
import com.nexus.model.RequestStatus;
import com.nexus.model.Scope;
import com.nexus.model.ZoneType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * RF-37 / RF-40 - Parametros de una solicitud de recomendacion (CU36, CU39).
 *
 * <p>Solo guarda valores cerrados (RN-32). No guarda la ubicacion de quien pide: con
 * {@link ZoneType#NEAR_US} se usa al calcular y se descarta (RN-30).
 */
@Entity
@Table(name = "recommendation_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_id", nullable = false)
    private Link link;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecommendationMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false)
    private ZoneType zoneType;

    /** Solo con {@link ZoneType#BOROUGH}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borough_id")
    private Borough borough;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Scope scope;

    /** Tope de presupuesto por persona en pesos, de 0 (solo gratis) a 15 000. */
    @Column(name = "max_cost_mxn", nullable = false)
    private Integer maxCostMxn;

    /** Nombre de {@link com.nexus.model.IdeaCategory}; {@code null} = cualquiera. */
    private String category;

    @Column(name = "slot_start", nullable = false)
    private Instant slotStart;

    @Column(name = "slot_end", nullable = false)
    private Instant slotEnd;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.OPEN;

    /** Bloqueo optimista: dos "buscar mas opciones" simultaneos no pueden pasar los dos (R15). */
    @Version
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
