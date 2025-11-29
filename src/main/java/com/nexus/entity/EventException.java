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
@Table(name = "event_exceptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EventException {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    @Column(name = "exception_date", nullable = false)
    private Instant exceptionDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false)
    @Builder.Default
    private ExceptionType exceptionType = ExceptionType.DELETED;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    public enum ExceptionType {
        DELETED,    // Instancia eliminada
        MODIFIED    // Instancia modificada (futura implementación)
    }
}
