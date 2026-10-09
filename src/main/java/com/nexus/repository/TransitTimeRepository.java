package com.nexus.repository;

import com.nexus.entity.TransitTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Matriz dirigida de tiempos de traslado entre alcaldías (nexus-KB-02, K4). */
@Repository
public interface TransitTimeRepository extends JpaRepository<TransitTime, Long> {

    Optional<TransitTime> findByOriginBoroughAndDestBorough(String originBorough, String destBorough);
}
