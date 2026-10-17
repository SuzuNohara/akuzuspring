package com.nexus.repository;

import com.nexus.entity.PlanningVote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanningVoteRepository extends JpaRepository<PlanningVote, PlanningVote.Key> {

    List<PlanningVote> findBySessionId(Long sessionId);
}
