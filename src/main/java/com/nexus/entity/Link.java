package com.nexus.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "user_links")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Link {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_user_id", nullable = false)
    private User initiatorUser;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_user_id", nullable = false)
    private User partnerUser;
    
    @Column(name = "code_in_use", nullable = false, length = 32)
    private String codeInUse;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    
    @Column(name = "ended_at")
    private Instant endedAt;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
