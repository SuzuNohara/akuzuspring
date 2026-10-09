package com.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Actividad del banco de ideas (nexus-KB-02, §3.10.2 / CU34). Entidad de solo lectura. {@code
 * bankCategory} es la categoría que ve el usuario; {@code wellnessDimension} (NOT NULL, K1) es
 * interna para el agente. El costo es por pareja (K2): {@code costMaxMxn} es lo que filtra AGENT-03.
 */
@Entity
@Immutable
@Table(name = "activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Activity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "slug")
    private String slug;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_category")
    private BankCategory bankCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "wellness_dimension", nullable = false)
    private WellnessDimension wellnessDimension;

    @Column(name = "activity_type")
    private String activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_scope")
    private LocationScope locationScope;

    @Column(name = "cost_min_mxn")
    private Integer costMinMxn;

    @Column(name = "cost_max_mxn")
    private Integer costMaxMxn;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_band")
    private PriceBand priceBand;

    @Convert(converter = AmbienceSetConverter.class)
    @Column(name = "ambience")
    private Set<Ambience> ambience;

    @Convert(converter = WeatherOkSetConverter.class)
    @Column(name = "weather_ok")
    private Set<WeatherOk> weatherOk;

    @Column(name = "is_outdoor")
    private Boolean isOutdoor;

    @Column(name = "duration_min")
    private Integer durationMinMin;

    @Column(name = "duration_avg")
    private Integer durationAvgMin;

    @Column(name = "duration_max")
    private Integer durationMaxMin;

    @Column(name = "canonical_text")
    private String canonicalText;

    @Column(name = "canonical_hash")
    private String canonicalHash;

    @Column(name = "canonical_version")
    private Integer canonicalVersion;

    @Column(name = "needs_review")
    private Boolean needsReview;
}
