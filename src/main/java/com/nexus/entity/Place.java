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
 * add_kb_tables.sql). Solo lectura; se mapean las columnas que usa el filtro de proximidad y,
 * desde [kb-lugares] U8, las que ve la pareja en la tarjeta y en el evento.
 *
 * <p>[kb-lugares] Lugares reales de la CDMX (OpenStreetMap e INEGI DENUE), ver
 * seed_places_kb_final.sql. Direccion, colonia, web y telefono pueden faltar.
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

    /** [kb-lugares] U8 - Calle y numero; {@code null} si no se conoce. */
    private String address;

    /** [kb-lugares] U8 - Colonia. */
    private String neighborhood;

    /** [kb-lugares] U8 - Sitio web tal como viene de la fuente (la app solo abre http/https). */
    private String website;

    /** [kb-lugares] U8 - Telefono tal como viene de la fuente. */
    private String phone;
}
