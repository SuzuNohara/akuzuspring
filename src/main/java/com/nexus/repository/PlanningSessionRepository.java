package com.nexus.repository;

import com.nexus.entity.PlanningSession;
import com.nexus.model.PlanningStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanningSessionRepository extends JpaRepository<PlanningSession, Long> {

    /** La planeacion abierta del vinculo (hay a lo mas una). */
    Optional<PlanningSession> findFirstByLinkIdAndStatusInOrderByIdDesc(Long linkId, Collection<PlanningStatus> statuses);

    /** La planeacion mas reciente del vinculo, abierta o no (para avisar como termino). */
    Optional<PlanningSession> findFirstByLinkIdOrderByIdDesc(Long linkId);

    List<PlanningSession> findByStatusIn(Collection<PlanningStatus> statuses);

    // Las marcas de "visto" se escriben sin tocar la version: la app consulta cada pocos segundos y
    // no debe hacer fallar por bloqueo optimista una accion real de la pareja.

    @Modifying
    @Query("UPDATE PlanningSession s SET s.inviterSeenAt = :now WHERE s.id = :id")
    void markInviterSeen(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE PlanningSession s SET s.partnerSeenAt = :now WHERE s.id = :id")
    void markPartnerSeen(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE PlanningSession s SET s.inviterChatSeenAt = :now WHERE s.id = :id")
    void markInviterChatSeen(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE PlanningSession s SET s.partnerChatSeenAt = :now WHERE s.id = :id")
    void markPartnerChatSeen(@Param("id") Long id, @Param("now") Instant now);
}
