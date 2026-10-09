package com.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Vínculo entre una actividad y un lugar donde puede realizarse (nexus-KB-02). PK compuesta, al
 * estilo de {@code UserPreference}. Solo lectura.
 */
@Entity
@Immutable
@Table(name = "activity_places")
@IdClass(ActivityPlaceId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityPlace {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(name = "cost_override")
    private Integer costOverride;

    @Column(name = "notes")
    private String notes;

    @Column(name = "source")
    private String source;
}
