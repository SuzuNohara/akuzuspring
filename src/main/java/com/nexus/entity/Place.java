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
 * Lugar de la CDMX (nexus-KB-02, §3.10.2). Entidad de solo lectura: la fuente de verdad es {@code
 * kb/places.csv} cargado por el pipeline. El agente y AGENT-03 solo leen.
 */
@Entity
@Immutable
@Table(name = "places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "slug")
    private String slug;

    @Column(name = "name")
    private String name;

    @Column(name = "place_type")
    private String placeType;

    @Column(name = "neighborhood")
    private String neighborhood;

    @Column(name = "borough")
    private String borough;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lon")
    private Double lon;

    @Column(name = "opening_hours")
    private String openingHours;

    @Convert(converter = AmbienceSetConverter.class)
    @Column(name = "ambience")
    private Set<Ambience> ambience;

    @Convert(converter = WeatherOkSetConverter.class)
    @Column(name = "weather_ok")
    private Set<WeatherOk> weatherOk;

    @Column(name = "is_outdoor")
    private Boolean isOutdoor;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_band")
    private PriceBand priceBand;

    @Column(name = "canonical_text")
    private String canonicalText;

    @Column(name = "canonical_hash")
    private String canonicalHash;

    @Column(name = "canonical_version")
    private Integer canonicalVersion;

    @Column(name = "needs_review")
    private Boolean needsReview;
}
