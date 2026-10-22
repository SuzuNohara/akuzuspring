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
 * RF-36 - Lugar del catalogo donde puede ocurrir una actividad (tabla {@code places},
 * add_kb_tables.sql). Solo lectura; se mapean las columnas que usa el filtro de proximidad.
 *
 * <p>Mientras no llegue el catalogo real, las coordenadas son las del centro de la alcaldia
 * ({@code source = 'approx-borough'}), ver seed_boroughs_places.sql.
 */
@Entity
@Immutable
@Table(name = "places")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Place {

    @Id private Long id;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    /** Nombre de la alcaldia. */
    @Column(nullable = false)
    private String borough;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lon;

    /** [t06b-agente-java] Ambiente del lugar ({@code QUIET|SOCIAL}...); se suma al de la actividad. */
    @Column(nullable = false)
    private String ambience;
}
