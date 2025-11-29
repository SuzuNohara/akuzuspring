package com.nexus.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "link_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LinkCode {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "code", nullable = false, unique = true, length = 8)
    private String code;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by_user_id", nullable = false)
    private User generatedByUser;
    
    @Column(name = "is_used", nullable = false)
    private Boolean isUsed = false;
    
    @Column(name = "used_by_user_id")
    private Long usedByUserId;
    
    @Column(name = "used_at")
    private Instant usedAt;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    // RN-09: El código expira en 15 minutos
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    // RN-09: El código solo puede usarse una vez
    public boolean canBeUsed() {
        return !isUsed && !isExpired();
    }
}
