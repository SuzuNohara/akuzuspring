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
 * Entidad que mapea la tabla 'calendar_events' existente en la BD
 * Adaptada para soportar eventos externos importados
 */
@Entity
@Table(name = "calendar_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ExternalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_id", nullable = false)
    private Long externalCalendarId;

    @Column(name = "device_event_id", length = 255)
    private String deviceEventId; // ID del evento en el dispositivo

    @Column(name = "title", nullable = false, length = 240)
    private String title;

    @Column(name = "starts_at_utc", nullable = false)
    private Instant startDatetime;

    @Column(name = "ends_at_utc", nullable = false)
    private Instant endDatetime;

    @Column(name = "start_tzid", nullable = false, length = 64)
    private String startTimezone = "UTC";

    @Column(name = "end_tzid", nullable = false, length = 64)
    private String endTimezone = "UTC";

    @Column(name = "all_day", nullable = false)
    private Boolean isAllDay = false;

    @Column(name = "rrule", length = 600)
    private String recurrenceRule;

    @Column(name = "rrule_dtstart_utc")
    private Instant rruleDtstartUtc; // Fecha de inicio de la recurrencia

    @Column(name = "rrule_until_utc")
    private Instant rruleUntilUtc; // Fecha de fin de la recurrencia (si usa UNTIL)

    @Column(name = "rrule_count")
    private Integer rruleCount; // Número de ocurrencias (si usa COUNT)

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_external", nullable = false)
    private Boolean isExternal = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20)
    private Visibility visibility = Visibility.DEFAULT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private EventStatus status = EventStatus.CONFIRMED;

    @Column(name = "last_device_update")
    private Instant lastDeviceUpdate; // Para detectar cambios externos (RF-24)

    @Column(name = "sync_hash", length = 64)
    private String syncHash; // Hash para detectar cambios más eficientemente

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public enum Visibility {
        DEFAULT,
        PUBLIC,
        PRIVATE
    }

    public enum EventStatus {
        CONFIRMED,
        TENTATIVE,
        CANCELLED
    }
}
