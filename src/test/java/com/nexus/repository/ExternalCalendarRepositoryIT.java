package com.nexus.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.config.JpaAuditingConfig;
import com.nexus.entity.ExternalCalendar;
import com.nexus.entity.ExternalCalendar.CalendarSource;
import com.nexus.entity.ExternalCalendar.PrivacyMode;
import com.nexus.entity.User;
import com.nexus.harness.MySqlSchemaSupport;
import com.nexus.harness.TestCalendars;
import com.nexus.harness.TestUsers;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Contrato entre la entidad {@link ExternalCalendar} y la tabla {@code calendars} completada por
 * {@code db/add_calendars_external_columns.sql}, sobre MySQL 8 real.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ExternalCalendarRepositoryIT extends MySqlSchemaSupport {

  @Autowired ExternalCalendarRepository repository;
  @Autowired TestEntityManager em;

  @Test
  @DisplayName("should persist entity defaults when a calendar is linked")
  void shouldPersistEntityDefaultsWhenCalendarIsLinked() {
    // Given: un calendario recien vinculado, solo con lo que rellena linkCalendar
    User user = TestUsers.persist(em, "vinculo@prueba.test");
    ExternalCalendar linked = TestCalendars.persist(em, user.getId());
    em.clear();

    // When
    ExternalCalendar reloaded =
        repository.findByUserIdAndDeviceCalendarId(user.getId(), linked.getDeviceCalendarId())
            .orElseThrow();

    // Then: los defaults de la entidad llegan a la tabla y vuelven intactos
    assertThat(reloaded.getSource()).isEqualTo(CalendarSource.LOCAL);
    assertThat(reloaded.getSyncEnabled()).isTrue();
    assertThat(reloaded.getPrivacyMode()).isEqualTo(PrivacyMode.BUSY_ONLY);
    assertThat(reloaded.getIsActive()).isTrue();
    assertThat(reloaded.getLastSync()).isNull();
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.isExternal()).isTrue();
    assertThat(repository.existsByUserIdAndDeviceCalendarId(user.getId(), linked.getDeviceCalendarId()))
        .isTrue();
  }

  @Test
  @DisplayName("should persist privacy mode when it is changed after linking")
  void shouldPersistPrivacyModeWhenItIsChangedAfterLinking() {
    // Given: un calendario vinculado con la privacidad por defecto
    User user = TestUsers.persist(em, "privacidad@prueba.test");
    ExternalCalendar linked = TestCalendars.persist(em, user.getId());
    em.clear();

    // When: el usuario abre los detalles (PATCH privacyMode=FULL_DETAILS)
    ExternalCalendar managed = repository.findById(linked.getId()).orElseThrow();
    managed.setPrivacyMode(PrivacyMode.FULL_DETAILS);
    managed.setLastSync(Instant.parse("2026-09-16T00:00:00Z"));
    repository.saveAndFlush(managed);
    em.clear();

    // Then
    ExternalCalendar reloaded = repository.findById(linked.getId()).orElseThrow();
    assertThat(reloaded.getPrivacyMode()).isEqualTo(PrivacyMode.FULL_DETAILS);
    assertThat(reloaded.getLastSync()).isEqualTo(Instant.parse("2026-09-16T00:00:00Z"));
  }

  @Test
  @DisplayName("should round trip uppercase source when source is not the default")
  void shouldRoundTripUppercaseSourceWhenSourceIsNotDefault() {
    // Given: @Enumerated(STRING) escribe el literal en mayusculas
    User user = TestUsers.persist(em, "origen@prueba.test");
    ExternalCalendar linked = TestCalendars.persist(em, user.getId());
    linked.setSource(CalendarSource.GOOGLE);
    repository.saveAndFlush(linked);
    em.clear();

    // When
    ExternalCalendar reloaded = repository.findById(linked.getId()).orElseThrow();

    // Then: la columna ya no es un ENUM en minusculas y valueOf lee lo que se escribio
    assertThat(reloaded.getSource()).isEqualTo(CalendarSource.GOOGLE);
  }

  @Test
  @DisplayName("should return only active calendars when user has inactive and soft deleted ones")
  void shouldReturnOnlyActiveCalendarsWhenUserHasInactiveAndSoftDeletedOnes() {
    // Given: uno activo, uno desvinculado (is_active = 0) y uno borrado en suave
    User user = TestUsers.persist(em, "activos@prueba.test");
    ExternalCalendar active = TestCalendars.persist(em, user.getId());
    ExternalCalendar unlinked = TestCalendars.persist(em, user.getId());
    unlinked.setIsActive(false);
    ExternalCalendar deleted = TestCalendars.persist(em, user.getId());
    deleted.setDeletedAt(Instant.parse("2026-09-16T00:00:00Z"));
    em.flush();
    em.clear();

    // When
    List<ExternalCalendar> found = repository.findByUserIdAndIsActiveTrue(user.getId());

    // Then: findByUserId ve las tres filas; el filtro de activos solo la viva y activa
    assertThat(repository.findByUserId(user.getId())).hasSize(3);
    assertThat(found)
        .extracting(ExternalCalendar::getDeviceCalendarId)
        .containsExactly(active.getDeviceCalendarId());
  }
}
