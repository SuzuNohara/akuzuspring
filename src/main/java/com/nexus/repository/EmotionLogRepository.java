package com.nexus.repository;

import com.nexus.entity.EmotionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmotionLogRepository extends JpaRepository<EmotionLog, Long> {

    /**
     * Historial de un usuario, del mas reciente al mas antiguo (RF-32).
     */
    List<EmotionLog> findByUserIdOrderByLoggedAtDesc(Long userId);
}
