package com.nexus.repository;

import com.nexus.entity.BoroughCentroid;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Centroides de las alcaldías (nexus-KB-02). */
@Repository
public interface BoroughCentroidRepository extends JpaRepository<BoroughCentroid, Long> {

    Optional<BoroughCentroid> findByBorough(String borough);
}
