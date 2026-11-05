package com.nexus.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.config.JpaAuditingConfig;
import com.nexus.entity.User;
import com.nexus.harness.MySqlSchemaSupport;
import com.nexus.harness.TestUsers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * [primeros-pasos] R-03: el hito «Ajusta tus horarios» se guarda en {@code
 * users.schedule_customized_at} ({@code db/add_schedule_customized_at.sql}) en vez de adivinarse con
 * {@code created_at}, sobre MySQL 8 real.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class UserScheduleCustomizedIT extends MySqlSchemaSupport {

  private static final List<String> WEEK =
      List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

  @Autowired UserRepository users;
  @Autowired TestEntityManager em;

  @Test
  @DisplayName("should start empty and keep the instant when the schedule is marked as customized")
  void shouldStartEmptyAndKeepInstantWhenMarked() {
    User user = TestUsers.persist(em, "r03-marca@prueba.test");
    em.clear();
    assertThat(users.findById(user.getId()).orElseThrow().getScheduleCustomizedAt()).isNull();

    Instant at = Instant.parse("2026-11-05T10:15:30.123Z");
    assertThat(users.markScheduleCustomized(user.getId(), at)).isEqualTo(1);
    em.clear();

    assertThat(users.findById(user.getId()).orElseThrow().getScheduleCustomizedAt()).isEqualTo(at);
  }

  @Test
  @DisplayName("should stay empty when schedule rows are recreated by another path")
  void shouldStayEmptyWhenRowsAreRecreatedByAnotherPath() {
    // Given: filas creadas sin pasar por saveUserSchedule (trigger, reset, re-seed)
    User user = TestUsers.persist(em, "r03-otro-camino@prueba.test");
    week(user, true, "09:00:00", "22:00:00");
    em.clear();

    assertThat(users.findById(user.getId()).orElseThrow().getScheduleCustomizedAt()).isNull();
  }

  @Test
  @DisplayName("should backfill only users whose rows differ from the factory pattern")
  void shouldBackfillOnlyUsersWhoseRowsDifferFromFactoryPattern() throws IOException {
    User factory = TestUsers.persist(em, "r03-fabrica@prueba.test");
    week(factory, true, "09:00:00", "22:00:00");
    User changedHours = TestUsers.persist(em, "r03-horas@prueba.test");
    week(changedHours, true, "10:00:00", "20:00:00");
    User oneDayOff = TestUsers.persist(em, "r03-dia@prueba.test");
    week(oneDayOff, true, "09:00:00", "22:00:00");
    em.getEntityManager()
        .createNativeQuery(
            "UPDATE availability_schedules SET is_enabled = 0 WHERE user_id = ?1 AND day_of_week = 'SUNDAY'")
        .setParameter(1, oneDayOff.getId())
        .executeUpdate();
    User fewerDays = TestUsers.persist(em, "r03-menos@prueba.test");
    row(fewerDays, "MONDAY", true, "09:00:00", "22:00:00");
    User noRows = TestUsers.persist(em, "r03-vacio@prueba.test");
    User alreadyMarked = TestUsers.persist(em, "r03-ya@prueba.test");
    week(alreadyMarked, true, "08:00:00", "12:00:00");
    Instant kept = Instant.parse("2026-01-01T00:00:00Z");
    users.markScheduleCustomized(alreadyMarked.getId(), kept);

    em.getEntityManager().createNativeQuery(backfillSql()).executeUpdate();
    em.clear();

    assertThat(customizedAt(factory)).isNull();
    assertThat(customizedAt(changedHours)).isNotNull();
    assertThat(customizedAt(oneDayOff)).isNotNull();
    assertThat(customizedAt(fewerDays)).isNotNull();
    assertThat(customizedAt(noRows)).isNull();
    assertThat(customizedAt(alreadyMarked)).isEqualTo(kept);
  }

  private Instant customizedAt(User u) {
    return users.findById(u.getId()).orElseThrow().getScheduleCustomizedAt();
  }

  private void week(User user, boolean enabled, String start, String end) {
    for (String day : WEEK) {
      row(user, day, enabled, start, end);
    }
  }

  private void row(User user, String day, boolean enabled, String start, String end) {
    em.getEntityManager()
        .createNativeQuery(
            "INSERT INTO availability_schedules (user_id, day_of_week, is_enabled, start_time, end_time)"
                + " VALUES (?1, ?2, ?3, ?4, ?5)")
        .setParameter(1, user.getId())
        .setParameter(2, day)
        .setParameter(3, enabled)
        .setParameter(4, start)
        .setParameter(5, end)
        .executeUpdate();
  }

  /** La unica sentencia de {@code db/backfill_schedule_customized_at.sql}, sin los comentarios. */
  private static String backfillSql() throws IOException {
    try (InputStream in =
        UserScheduleCustomizedIT.class.getResourceAsStream("/db/backfill_schedule_customized_at.sql")) {
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return text.lines()
          .filter(l -> !l.stripLeading().startsWith("--"))
          .collect(Collectors.joining("\n"))
          .trim()
          .replaceAll(";$", "");
    }
  }
}
