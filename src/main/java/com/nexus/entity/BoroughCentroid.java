package com.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Centroide de una de las 16 alcaldías (nexus-KB-02, A1). "Centro de la alcaldía" para la
 * proximidad de AGENT-03 (RN-31). Solo lectura.
 */
@Entity
@Immutable
@Table(name = "borough_centroids")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoroughCentroid {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "borough")
    private String borough;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lon")
    private Double lon;

    @Column(name = "source")
    private String source;
}
