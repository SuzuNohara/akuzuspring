package com.nexus.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.config.JpaAuditingConfig;
import com.nexus.entity.AvailabilitySchedule;
import com.nexus.entity.AvailabilitySchedule.DayOfWeek;
import com.nexus.entity.User;
import com.nexus.harness.MySqlSchemaSupport;
import com.nexus.harness.TestUsers;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Contrato entre la entidad {@link AvailabilitySchedule} y la tabla {@code availability_schedules}
 * creada por {@code db/add_availability_schedules.sql}, sobre MySQL 8 real.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class AvailabilityScheduleRepositoryIT extends MySqlSchemaSupport {

  private static final LocalTime START = LocalTime.of(9, 0);
  private static final LocalTime END = LocalTime.of(18, 0);

  @Autowired AvailabilityScheduleRepository repository;
  @Autowired TestEntityManager em;

  @Test
  @DisplayName("should return only enabled days in week order when user has mixed rows")
  void shouldReturnOnlyEnabledDaysInWeekOrderWhenUserHasMixedRows() {
    // Given: cuatro filas insertadas fuera de orden, dos habilitadas y dos no
    User user = TestUsers.persist(em, "orden@prueba.test");
    em.persist(row(user, DayOfWeek.THURSDAY, true));
    em.persist(row(user, DayOfWeek.MONDAY, false));
    em.persist(row(user, DayOfWeek.SUNDAY, false));
    em.persist(row(user, DayOfWeek.TUESDAY, true));
    em.flush();
    em.clear();

    // When
    List<AvailabilitySchedule> enabled = repository.findEnabledByUserId(user.getId());

    // Then: solo las habilitadas, ordenadas por el ENUM (orden de declaracion), no alfabeticamente
    assertThat(enabled)
        .extracting(AvailabilitySchedule::getDayOfWeek)
        .containsExactly(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
  }

  @Test
  @DisplayName("should round trip times and flags when a row is persisted and reloaded")
  void shouldRoundTripTimesAndFlagsWhenRowIsPersistedAndReloaded() {
    // Given
    User user = TestUsers.persist(em, "idavuelta@prueba.test");
    AvailabilitySchedule saved = em.persistAndFlush(row(user, DayOfWeek.FRIDAY, false));
    em.clear();

    // When
    AvailabilitySchedule reloaded = repository.findById(saved.getId()).orElseThrow();

    // Then: TIME, TINYINT(1) y los timestamps de Hibernate vuelven intactos
    assertThat(reloaded.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
    assertThat(reloaded.getIsEnabled()).isFalse();
    assertThat(reloaded.getStartTime()).isEqualTo(START);
    assertThat(reloaded.getEndTime()).isEqualTo(END);
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.getUpdatedAt()).isNotNull();
    assertThat(reloaded.getUser().getId()).isEqualTo(user.getId());
  }

  @Test
  @DisplayName("should reject a second row when user and day are repeated")
  void shouldRejectSecondRowWhenUserAndDayAreRepeated() {
    // Given: ya existe WEDNESDAY para el usuario
    User user = TestUsers.persist(em, "duplicado@prueba.test");
    repository.saveAndFlush(row(user, DayOfWeek.WEDNESDAY, true));

    // When / Then: uk_avail_user_day (user_id, day_of_week) la rechaza
    assertThatThrownBy(() -> repository.saveAndFlush(row(user, DayOfWeek.WEDNESDAY, false)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should allow the same day when it belongs to two different users")
  void shouldAllowSameDayWhenItBelongsToTwoDifferentUsers() {
    // Given
    User first = TestUsers.persist(em, "uno@prueba.test");
    User second = TestUsers.persist(em, "dos@prueba.test");

    // When
    repository.saveAndFlush(row(first, DayOfWeek.MONDAY, true));
    repository.saveAndFlush(row(second, DayOfWeek.MONDAY, true));
    em.clear();

    // Then: la unicidad es por (usuario, dia), no por dia
    assertThat(repository.findByUserId(first.getId())).hasSize(1);
    assertThat(repository.findByUserId(second.getId())).hasSize(1);
  }

  @Test
  @DisplayName("should cascade delete schedules when the user row is deleted natively")
  void shouldCascadeDeleteSchedulesWhenUserRowIsDeletedNatively() {
    // Given: un usuario con dos filas ya en la base
    User user = TestUsers.persist(em, "cascada@prueba.test");
    repository.saveAndFlush(row(user, DayOfWeek.SATURDAY, true));
    repository.saveAndFlush(row(user, DayOfWeek.SUNDAY, true));
    Long userId = user.getId();
    em.clear();

    // When: borrado nativo, sin pasar por JPA, para probar fk_avail_user ON DELETE CASCADE
    int deletedUsers =
        em.getEntityManager()
            .createNativeQuery("DELETE FROM users WHERE id = :id")
            .setParameter("id", userId)
            .executeUpdate();

    // Then
    assertThat(deletedUsers).isEqualTo(1);
    assertThat(repository.findByUserId(userId)).isEmpty();
  }

  private static AvailabilitySchedule row(User user, DayOfWeek day, boolean enabled) {
    return AvailabilitySchedule.builder()
        .user(user)
        .dayOfWeek(day)
        .isEnabled(enabled)
        .startTime(START)
        .endTime(END)
        .build();
  }
}
