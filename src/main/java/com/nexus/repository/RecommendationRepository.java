package com.nexus.repository;

import com.nexus.entity.Recommendation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    /** Todo lo mostrado en una solicitud, en todas sus busquedas (RN-39: no repetir). */
    List<Recommendation> findByRequestId(Long requestId);
}
