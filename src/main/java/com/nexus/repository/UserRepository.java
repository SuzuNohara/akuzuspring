package com.nexus.repository;

import com.nexus.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByLinkCode(String linkCode);
    
    boolean existsByEmail(String email);
    
    boolean existsByLinkCode(String linkCode);
    
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findActiveByEmail(@Param("email") String email);
    
    @Query("SELECT u FROM User u WHERE u.linkCode = :linkCode AND u.deletedAt IS NULL")
    Optional<User> findActiveByLinkCode(@Param("linkCode") String linkCode);
    
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findActiveById(@Param("id") Long id);
    
    /** [push-avisos] R7: un telefono solo recibe los avisos de la ultima cuenta que entro en el. */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.fcmToken = NULL WHERE u.fcmToken = :token AND u.id <> :userId")
    int clearTokenFromOthers(@Param("token") String token, @Param("userId") Long userId);
    
    /** [push-avisos] R8: quita el token muerto solo si el usuario no registro otro mientras tanto. */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.fcmToken = NULL WHERE u.id = :userId AND u.fcmToken = :token")
    int clearTokenIfMatches(@Param("userId") Long userId, @Param("token") String token);
    
    /** [push-avisos] U9: al cerrar sesion el telefono deja de recibir avisos. */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.fcmToken = NULL WHERE u.id = :userId")
    int clearToken(@Param("userId") Long userId);

    /** [primeros-pasos] R-03: la persona guardo sus horarios; el hito «Ajusta tus horarios» queda hecho. */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.scheduleCustomizedAt = :at WHERE u.id = :userId")
    int markScheduleCustomized(@Param("userId") Long userId, @Param("at") java.time.Instant at);
}
