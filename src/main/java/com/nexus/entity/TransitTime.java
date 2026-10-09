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
 * Celda de la matriz dirigida de tiempos de traslado en transporte público entre alcaldías
 * (nexus-KB-02, K4/DV-07). 16 × 16 = 256 pares. {@code source} = {@code eod2017} o {@code
 * formula:v1}. Solo lectura; AGENT-03 carga las 256 celdas una vez.
 */
@Entity
@Immutable
@Table(name = "transit_times")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransitTime {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "origin_borough")
    private String originBorough;

    @Column(name = "dest_borough")
    private String destBorough;

    @Column(name = "minutes_p50")
    private Integer minutesP50;

    @Column(name = "minutes_p75")
    private Integer minutesP75;

    @Column(name = "source")
    private String source;

    @Column(name = "n_trips")
    private Integer tripCount;
}
