package com.nexus.entity;

import com.nexus.model.Vote;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

/** RF-39 - Voto de una persona por una opcion de la planeacion en pareja. */
@Entity
@Table(name = "planning_votes")
@IdClass(PlanningVote.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanningVote {

    @Id
    @Column(name = "recommendation_id")
    private Long recommendationId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Vote vote;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long recommendationId;
        private Long userId;
    }
}
