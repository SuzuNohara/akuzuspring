package com.nexus.repository;

import com.nexus.entity.LinkCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LinkCodeRepository extends JpaRepository<LinkCode, Long> {
    
    Optional<LinkCode> findByCode(String code);
    
    boolean existsByCode(String code);
    
    // Encontrar el código activo más reciente de un usuario
    @Query("SELECT lc FROM LinkCode lc WHERE lc.generatedByUser.id = :userId AND lc.isUsed = false AND lc.expiresAt > CURRENT_TIMESTAMP ORDER BY lc.createdAt DESC")
    Optional<LinkCode> findActiveCodeByUserId(@Param("userId") Long userId);
    
    // Eliminar códigos expirados (para limpieza)
    @Query("DELETE FROM LinkCode lc WHERE lc.expiresAt < CURRENT_TIMESTAMP")
    void deleteExpiredCodes();
    
    // Eliminar todos los códigos generados por un usuario (para borrado en cascada)
    void deleteByGeneratedByUserId(Long userId);
}
