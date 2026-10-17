package com.nexus.repository;

import com.nexus.entity.RecommendationRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationRequestRepository extends JpaRepository<RecommendationRequest, Long> {

    /**
     * Carga la solicitud subiendo su version al confirmar: si dos peticiones la cambian a la vez
     * (doble toque en "Buscar mas opciones"), la segunda falla con conflicto (R15).
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT r FROM RecommendationRequest r WHERE r.id = :id")
    Optional<RecommendationRequest> findLockedById(@Param("id") Long id);
}
