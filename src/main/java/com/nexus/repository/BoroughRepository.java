package com.nexus.repository;

import com.nexus.entity.Borough;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoroughRepository extends JpaRepository<Borough, Long> {

    Optional<Borough> findByCode(String code);

    /** Las 16 alcaldias en orden alfabetico, para elegir la zona de la cita (CU36). */
    List<Borough> findAllByOrderByNameAsc();
}
