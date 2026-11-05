package com.nexus.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.config.JpaAuditingConfig;
import com.nexus.entity.User;
import com.nexus.harness.MySqlSchemaSupport;
import com.nexus.harness.TestUsers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * [primeros-pasos] R-04: «Primera cita calificada» cuenta solo citas del vinculo dado, sobre MySQL 8
 * real ({@code user_links}, {@code events} de {@code Nexus.sql} y {@code date_ratings} de {@code
 * add_date_ratings.sql}). Las filas van por SQL nativo: solo importan las columnas que unen.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class DateRatingRepositoryIT extends MySqlSchemaSupport {

  @Autowired DateRatingRepository repository;
  @Autowired TestEntityManager em;

  @Test
  @DisplayName("should count only ratings of events of the given link when a user re-links")
  void shouldCountOnlyRatingsOfEventsOfTheGivenLinkWhenUserReLinks() {
    // Given: A tuvo un vinculo con B (inactivo) y ahora uno con C. Despues de vincularse con C,
    // A califica una cita del vinculo viejo.
    User a = TestUsers.persist(em, "r04-a@prueba.test");
    User b = TestUsers.persist(em, "r04-b@prueba.test");
    User c = TestUsers.persist(em, "r04-c@prueba.test");
    long oldLink = link(a, b, false);
    long newLink = link(a, c, true);
    long oldDate = event(a, oldLink, false);
    rate(oldDate, a);

    // Then: cuenta para el vinculo viejo, no para el nuevo
    assertThat(repository.existsForLink(oldLink)).isTrue();
    assertThat(repository.existsForLink(newLink)).isFalse();
  }

  @Test
  @DisplayName("should count a rating of either partner on an event of the link")
  void shouldCountRatingOfEitherPartnerOnEventOfTheLink() {
    User a = TestUsers.persist(em, "r04-d@prueba.test");
    User b = TestUsers.persist(em, "r04-e@prueba.test");
    long l = link(a, b, true);
    rate(event(a, l, false), b);

    assertThat(repository.existsForLink(l)).isTrue();
  }

  @Test
  @DisplayName("should ignore ratings on deleted events")
  void shouldIgnoreRatingsOnDeletedEvents() {
    User a = TestUsers.persist(em, "r04-f@prueba.test");
    User b = TestUsers.persist(em, "r04-g@prueba.test");
    long l = link(a, b, true);
    rate(event(a, l, true), a);

    assertThat(repository.existsForLink(l)).isFalse();
  }

  private long link(User initiator, User partner, boolean active) {
    em.getEntityManager()
        .createNativeQuery(
            "INSERT INTO user_links (initiator_user_id, partner_user_id, code_in_use, is_active)"
                + " VALUES (?1, ?2, 'R04', ?3)")
        .setParameter(1, initiator.getId())
        .setParameter(2, partner.getId())
        .setParameter(3, active)
        .executeUpdate();
    return lastId();
  }

  private long event(User creator, long linkId, boolean deleted) {
    em.getEntityManager()
        .createNativeQuery(
            "INSERT INTO events (title, start_date_time, end_date_time, creator_user_id, link_id,"
                + " status, deleted_at) VALUES ('Cita', '2026-10-10 20:00:00', '2026-10-10 22:00:00',"
                + " ?1, ?2, 'CONFIRMED', " + (deleted ? "CURRENT_TIMESTAMP(3)" : "NULL") + ")")
        .setParameter(1, creator.getId())
        .setParameter(2, linkId)
        .executeUpdate();
    return lastId();
  }

  private void rate(long eventId, User user) {
    em.getEntityManager()
        .createNativeQuery(
            "INSERT INTO date_ratings (event_id, user_id, encrypted_score) VALUES (?1, ?2, 'cifrado')")
        .setParameter(1, eventId)
        .setParameter(2, user.getId())
        .executeUpdate();
  }

  private long lastId() {
    return ((Number) em.getEntityManager().createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult())
        .longValue();
  }
}
