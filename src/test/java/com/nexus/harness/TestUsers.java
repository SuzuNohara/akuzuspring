package com.nexus.harness;

import com.nexus.entity.User;
import java.time.LocalDate;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Unico sitio que conoce las columnas obligatorias de {@code users} para los {@code *IT}.
 *
 * <p>Usa el constructor sin argumentos y no el builder de Lombok a proposito: los inicializadores
 * de campo de {@code User} ({@code emailConfirmed}, {@code linkCodeVersion}, {@code termsAccepted})
 * no llevan {@code @Builder.Default}, asi que el builder los dejaria a {@code null} sobre columnas
 * {@code NOT NULL}.
 */
public final class TestUsers {
  private TestUsers() {}

  /** Persiste un usuario minimo valido segun Nexus.sql (email, password_hash, display_name, link_code, birth_date). */
  public static User persist(TestEntityManager em, String email) {
    User u = new User();
    u.setEmail(email);
    u.setPasswordHash("$2a$10$inerte");
    u.setDisplayName("Prueba");
    u.setLinkCode("PRUE-" + Integer.toHexString(email.hashCode()));  // hasta que nexus-AUTH-10 retire el campo
    u.setBirthDate(LocalDate.of(1990, 1, 1));
    return em.persistAndFlush(u);
  }
}
