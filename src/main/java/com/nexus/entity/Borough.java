package com.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * RN-31 - Alcaldia de la CDMX y su punto central, para el modo "alcaldia" del filtro de
 * proximidad (tabla {@code boroughs}, add_recommendation_tables.sql). Solo lectura.
 */
@Entity
@Immutable
@Table(name = "boroughs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Borough {

    @Id private Long id;

    /** Codigo estable, p. ej. {@code ALVARO_OBREGON}. */
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lon;
}
