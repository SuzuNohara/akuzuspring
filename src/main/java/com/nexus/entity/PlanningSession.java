package com.nexus.entity;

import com.nexus.model.PlanningStatus;
import com.nexus.model.Scope;
import com.nexus.model.ZoneType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * RF-38 / RF-39 - Planeacion en pareja: invitacion (24 h), sesion (12 h desde que la pareja acepta),
 * filtros compartidos, opciones, propuesta y acuerdo. Las referencias son ids simples: la sesion
 * solo las lee y asi no arrastra cargas perezosas.
 *
 * <p>No guarda la ubicacion de nadie (RN-30): "Cerca de nosotros" vive en memoria del servidor.
 */
@Entity
@Table(name = "planning_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanningSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "inviter_user_id", nullable = false)
    private Long inviterId;

    @Column(name = "partner_user_id", nullable = false)
    private Long partnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanningStatus status;

    @Column(name = "invite_expires_at", nullable = false)
    private Instant inviteExpiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "session_expires_at")
    private Instant sessionExpiresAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type")
    private ZoneType zoneType;

    @Column(name = "borough_id")
    private Long boroughId;

    @Enumerated(EnumType.STRING)
    private Scope scope;

    @Column(name = "max_cost_mxn")
    private Integer maxCostMxn;

    /** Nombres de {@link com.nexus.model.IdeaCategory} separados por coma; vacio = cualquiera. */
    private String categories;

    @Column(name = "slot_start")
    private Instant slotStart;

    @Column(name = "slot_end")
    private Instant slotEnd;

    @Builder.Default
    @Column(name = "inviter_ready", nullable = false)
    private Boolean inviterReady = false;

    @Builder.Default
    @Column(name = "partner_ready", nullable = false)
    private Boolean partnerReady = false;

    /** Quien pidio ajustar filtros tras agotar las opciones (B7); {@code null} si nadie. */
    @Column(name = "adjusting_user_id")
    private Long adjustingUserId;

    /** Solicitud de opciones vigente ({@code recommendation_requests}, modo JOINT). */
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "proposal_recommendation_id")
    private Long proposalRecommendationId;

    @Column(name = "proposed_by_user_id")
    private Long proposedById;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "inviter_seen_at")
    private Instant inviterSeenAt;

    @Column(name = "partner_seen_at")
    private Instant partnerSeenAt;

    @Column(name = "inviter_chat_seen_at")
    private Instant inviterChatSeenAt;

    @Column(name = "partner_chat_seen_at")
    private Instant partnerChatSeenAt;

    /** Recordatorios ya enviados, separados por coma (p. ej. "INVITE_12H,SESSION_6H"). */
    @Builder.Default
    @Column(name = "reminders_sent", nullable = false)
    private String remindersSent = "";

    @Version
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isMember(Long userId) {
        return inviterId.equals(userId) || partnerId.equals(userId);
    }

    public Long otherThan(Long userId) {
        return inviterId.equals(userId) ? partnerId : inviterId;
    }

    public boolean isReady(Long userId) {
        return inviterId.equals(userId) ? inviterReady : partnerReady;
    }

    public void setReady(Long userId, boolean ready) {
        if (inviterId.equals(userId)) {
            inviterReady = ready;
        } else {
            partnerReady = ready;
        }
    }

    public Instant seenAt(Long userId) {
        return inviterId.equals(userId) ? inviterSeenAt : partnerSeenAt;
    }

    public Instant chatSeenAt(Long userId) {
        return inviterId.equals(userId) ? inviterChatSeenAt : partnerChatSeenAt;
    }

    public boolean reminderSent(String code) {
        return (',' + remindersSent + ',').contains(',' + code + ',');
    }

    public void addReminder(String code) {
        remindersSent = remindersSent.isEmpty() ? code : remindersSent + ',' + code;
    }
}
