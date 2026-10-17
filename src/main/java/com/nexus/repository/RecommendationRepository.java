package com.nexus.repository;

import com.nexus.entity.Recommendation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    /** Las 15 opciones guardadas de una solicitud, en el orden en que se muestran. */
    List<Recommendation> findByRequestIdOrderByPositionAsc(Long requestId);
}
