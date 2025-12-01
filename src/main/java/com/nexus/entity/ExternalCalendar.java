package com.nexus.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Entidad que mapea la tabla 'calendars' existente en la BD
 * Adaptada para soportar calendarios externos con expo-calendar
 */
@Entity
@Table(name = "calendars")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ExternalCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CalendarSource source = CalendarSource.LOCAL;

    @Column(name = "external_id", length = 190)
    private String externalId; // Para OAuth (Google, Outlook)

    @Column(name = "device_calendar_id", length = 255)
    private String deviceCalendarId; // ID del calendario en el dispositivo (expo-calendar)

    @Column(name = "name", nullable = false, length = 120)
    private String calendarName;

    @Column(name = "color_hex", length = 7)
    private String calendarColor;

    @Column(name = "sync_enabled", nullable = false)
    private Boolean syncEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy_mode", nullable = false, length = 20)
    private PrivacyMode privacyMode = PrivacyMode.BUSY_ONLY;

    @Column(name = "last_sync")
    private Instant lastSync;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public enum CalendarSource {
        LOCAL,
        GOOGLE,
        OUTLOOK
    }

    public enum PrivacyMode {
        FULL_DETAILS,  // Mostrar todos los detalles del evento
        BUSY_ONLY      // Mostrar solo "ocupado" sin detalles
    }

    // Helper method para saber si es un calendario externo
    public boolean isExternal() {
        return deviceCalendarId != null && !deviceCalendarId.isEmpty();
    }
}
