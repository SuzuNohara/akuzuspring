package com.nexus.repository;

import com.nexus.entity.RecommendationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationRequestRepository extends JpaRepository<RecommendationRequest, Long> {}
