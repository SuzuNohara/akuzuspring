package com.nexus.repository;

import com.nexus.entity.Place;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Acceso de solo lectura a los lugares (nexus-KB-02). */
@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findBySlug(String slug);

    List<Place> findByBorough(String borough);

    /** Prefiltro de caja para la proximidad de AGENT-03. */
    List<Place> findByLatBetweenAndLonBetween(
            Double latMin, Double latMax, Double lonMin, Double lonMax);
}
