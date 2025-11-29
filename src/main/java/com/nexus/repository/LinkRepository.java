package com.nexus.repository;

import com.nexus.entity.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LinkRepository extends JpaRepository<Link, Long> {
    
    // RN-08: Verificar si un usuario tiene un vínculo activo
    @Query("SELECT l FROM Link l " +
           "LEFT JOIN FETCH l.initiatorUser " +
           "LEFT JOIN FETCH l.partnerUser " +
           "WHERE (l.initiatorUser.id = :userId OR l.partnerUser.id = :userId) AND l.isActive = true AND l.deletedAt IS NULL")
    Optional<Link> findActiveLinkByUserId(@Param("userId") Long userId);
    
    // Verificar si dos usuarios ya están vinculados
    @Query("SELECT l FROM Link l " +
           "LEFT JOIN FETCH l.initiatorUser " +
           "LEFT JOIN FETCH l.partnerUser " +
           "WHERE ((l.initiatorUser.id = :user1Id AND l.partnerUser.id = :user2Id) OR (l.initiatorUser.id = :user2Id AND l.partnerUser.id = :user1Id)) AND l.isActive = true AND l.deletedAt IS NULL")
    Optional<Link> findActiveLinkBetweenUsers(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);
    
    // Verificar si existe vínculo activo
    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END FROM Link l WHERE (l.initiatorUser.id = :userId OR l.partnerUser.id = :userId) AND l.isActive = true AND l.deletedAt IS NULL")
    boolean existsActiveLinkByUserId(@Param("userId") Long userId);
    
    // Eliminar todos los vínculos donde participa el usuario (para borrado en cascada)
    void deleteByInitiatorUserIdOrPartnerUserId(Long initiatorUserId, Long partnerUserId);
}
