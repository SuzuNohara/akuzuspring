package com.nexus.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Clave compuesta de {@link ActivityPlace} (nexus-KB-02): ids de la actividad y del lugar. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ActivityPlaceId implements Serializable {

    private Long activity;
    private Long place;
}
