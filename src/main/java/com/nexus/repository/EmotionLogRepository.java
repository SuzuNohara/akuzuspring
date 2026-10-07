package com.nexus.repository;

import com.nexus.entity.EmotionLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmotionLogRepository extends JpaRepository<EmotionLog, Long> {

    /**
     * Historial de un usuario, del mas reciente al mas antiguo (RF-32).
     */
    List<EmotionLog> findByUserIdOrderByLoggedAtDesc(Long userId);

    /**
     * Registros de un usuario dentro de [from, to) -- se usa para el "mismo dia" de RN-38.
     */
    List<EmotionLog> findByUserIdAndLoggedAtGreaterThanEqualAndLoggedAtLessThan(
            Long userId, Instant from, Instant to);
}
