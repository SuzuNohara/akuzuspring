package com.nexus.repository;

import com.nexus.entity.PlanningMessage;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanningMessageRepository extends JpaRepository<PlanningMessage, Long> {

    List<PlanningMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    long countBySessionId(Long sessionId);

    long countBySessionIdAndSenderIdAndCreatedAtAfter(Long sessionId, Long senderId, Instant after);

    @Modifying
    void deleteBySessionId(Long sessionId);
}
