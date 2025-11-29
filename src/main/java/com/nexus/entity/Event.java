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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Event {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "title", nullable = false, length = 255)
    private String title;
    
    @Column(name = "start_date_time", nullable = false)
    private Instant startDateTime;
    
    @Column(name = "end_date_time", nullable = false)
    private Instant endDateTime;
    
    @Column(name = "location", length = 500)
    private String location;
    
    @Column(name = "category", length = 100)
    private String category;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private EventStatus status = EventStatus.PENDING;
    
    // Usuario que creó el evento
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_user_id", nullable = false)
    private User creator;
    
    // Link asociado (pareja)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_id", nullable = false)
    private Link link;
    
    // Usuario que creó el evento automáticamente aprueba
    @Column(name = "creator_approved", nullable = false)
    @Builder.Default
    private Boolean creatorApproved = true;
    
    // Aprobación de la pareja
    @Column(name = "partner_approved", nullable = false)
    @Builder.Default
    private Boolean partnerApproved = false;
    
    // Fecha de aprobación del creador (automática)
    @Column(name = "creator_approved_at")
    private Instant creatorApprovedAt;
    
    // Fecha de aprobación de la pareja
    @Column(name = "partner_approved_at")
    private Instant partnerApprovedAt;
    
    // Campos opcionales para futuras funcionalidades
    @Column(name = "is_recurring", nullable = false)
    @Builder.Default
    private Boolean isRecurring = false;
    
    @Column(name = "recurrence_pattern", length = 100)
    private String recurrencePattern; // Ej: "DAILY", "WEEKLY", "MONTHLY"
    
    @Column(name = "reminder_minutes")
    private Integer reminderMinutes; // Minutos antes del evento para recordatorio (deprecado, usar reminders)
    
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventReminder> reminders = new ArrayList<>();
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "color", length = 7)
    private String color; // Formato hex: #FF4F81
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    // Flag transitorio para prevenir actualización automática del status en @PreUpdate
    @Transient
    private boolean skipAutoStatusUpdate = false;
    
    // Getter y setter manuales para el flag transitorio
    public void setSkipAutoStatusUpdate(boolean skip) {
        this.skipAutoStatusUpdate = skip;
    }
    
    public boolean isSkipAutoStatusUpdate() {
        return this.skipAutoStatusUpdate;
    }
    
    // Método para verificar si el evento está completamente aprobado
    public boolean isFullyApproved() {
        return creatorApproved && partnerApproved;
    }
    
    // Método para obtener el usuario que falta por aprobar
    public Long getPendingApprovalUserId() {
        // Solo retornar el usuario pendiente si el evento está realmente PENDING
        // No confundir con REJECTED que también tiene partnerApproved=false
        if (status == EventStatus.PENDING && !partnerApproved) {
            // Necesitamos obtener el ID de la pareja
            User partner = getPartnerUser();
            return partner != null ? partner.getId() : null;
        }
        return null;
    }
    
    // Método auxiliar para obtener la pareja
    private User getPartnerUser() {
        if (link == null) return null;
        
        Long creatorId = creator.getId();
        if (creatorId.equals(link.getInitiatorUser().getId())) {
            return link.getPartnerUser();
        } else {
            return link.getInitiatorUser();
        }
    }
    
    @PrePersist
    protected void onCreate() {
        if (creatorApprovedAt == null && creatorApproved) {
            creatorApprovedAt = Instant.now();
        }
    }
    
    // DISABLED: @PreUpdate causaba que status se sobrescribiera a PENDING cuando partner_approved=false
    // La lógica de actualización de status ahora se maneja en EventService
    /*
    @PreUpdate
    protected void onUpdate() {
        // Actualizar timestamp de aprobación de la pareja
        if (partnerApproved && partnerApprovedAt == null) {
            partnerApprovedAt = Instant.now();
        }
        
        // NO actualizar status si ya es un estado terminal (REJECTED o CANCELLED)
        if (status == EventStatus.REJECTED || status == EventStatus.CANCELLED) {
            return; // Preservar estados terminales
        }
        
        // Solo actualizar status automáticamente para estados no terminales
        if (isFullyApproved()) {
            status = EventStatus.CONFIRMED;
        } else {
            status = EventStatus.PENDING;
        }
    }
    */
}