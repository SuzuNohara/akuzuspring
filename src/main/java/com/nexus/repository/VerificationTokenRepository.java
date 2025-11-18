package com.nexus.repository;

import com.nexus.entity.VerificationToken;
import com.nexus.entity.VerificationToken.TokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    
    Optional<VerificationToken> findByToken(String token);
    
    @Query("SELECT vt FROM VerificationToken vt WHERE vt.user.id = :userId " +
           "AND vt.purpose = :purpose AND vt.consumedAt IS NULL " +
           "AND vt.expiresAt > :now ORDER BY vt.createdAt DESC")
    Optional<VerificationToken> findLatestValidToken(
        @Param("userId") Long userId,
        @Param("purpose") TokenPurpose purpose,
        @Param("now") Instant now
    );
    
    @Query("SELECT vt FROM VerificationToken vt WHERE vt.user.id = :userId AND vt.purpose = :purpose")
    Optional<VerificationToken> findByUserIdAndPurpose(
        @Param("userId") Long userId,
        @Param("purpose") TokenPurpose purpose
    );
    
    void deleteByUserIdAndPurpose(Long userId, TokenPurpose purpose);
}
