package com.nexus.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.config.JpaAuditingConfig;
import com.nexus.entity.ExternalCalendar;
import com.nexus.entity.ExternalEvent;
import com.nexus.entity.ExternalEvent.EventStatus;
import com.nexus.entity.ExternalEvent.Visibility;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Contrato entre la entidad {@link ExternalEvent} y la tabla {@code calendar_events} completada por
 * {@code db/add_calendar_events_external_columns.sql}, sobre MySQL 8 real. Cubre en particular la
 * clave unica {@code uk_ce_calendar_device_live (calendar_id, device_event_id, is_live)}: rechaza
 * dos filas vivas del mismo evento, ignora las borradas en suave y los eventos propios sin
 * {@code device_event_id}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ExternalEventRepositoryIT extends MySqlSchemaSupport {

  private static final Instant START = Instant.parse("2026-10-01T10:00:00Z");
  private static final Instant END = Instant.parse("2026-10-01T11:00:00Z");
  private static final Instant RANGE_START = Instant.parse("2026-09-30T00:00:00Z");
  private static final Instant RANGE_END = Instant.parse("2026-10-02T00:00:00Z");
  private static final String DEVICE_EVENT_ID = "ev-schema02";
  private static final int LONG_DESCRIPTION_LENGTH = 300;
  private static final int SHA256_BASE64_LENGTH = 44;

  @Autowired ExternalEventRepository repository;
  @Autowired TestEntityManager em;

  @Test
  @DisplayName("should round trip sync columns when an external event is persisted and reloaded")
  void shouldRoundTripSyncColumnsWhenExternalEventIsPersistedAndReloaded() {
    // Given: un evento importado con todas las columnas de sincronizacion informadas
    ExternalCalendar calendar = calendar("idavuelta@prueba.test");
    String description = "d".repeat(LONG_DESCRIPTION_LENGTH); // TEXT: no cabe en VARCHAR(255)
    String syncHash = "A".repeat(SHA256_BASE64_LENGTH - 1) + "="; // SHA-256 en Base64
    Instant deviceUpdate = Instant.parse("2026-09-16T00:00:00.123Z");
    ExternalEvent event = event(calendar, DEVICE_EVENT_ID, EventStatus.TENTATIVE);
    event.setLocation("Sala 1");
    event.setDescription(description);
    event.setIsExternal(true);
    event.setVisibility(Visibility.PRIVATE);
    event.setLastDeviceUpdate(deviceUpdate);
    event.setSyncHash(syncHash);
    ExternalEvent saved = em.persistAndFlush(event);
    em.clear();

    // When
    ExternalEvent reloaded =
        repository
            .findByExternalCalendarIdAndDeviceEventId(calendar.getId(), DEVICE_EVENT_ID)
            .orElseThrow();

    // Then: VARCHAR, TEXT, TINYINT(1), DATETIME(3) y los dos enums en mayusculas vuelven intactos
    assertThat(reloaded.getId()).isEqualTo(saved.getId());
    assertThat(reloaded.getLocation()).isEqualTo("Sala 1");
    assertThat(reloaded.getDescription()).hasSize(LONG_DESCRIPTION_LENGTH).isEqualTo(description);
    assertThat(reloaded.getIsExternal()).isTrue();
    assertThat(reloaded.getVisibility()).isEqualTo(Visibility.PRIVATE);
    assertThat(reloaded.getStatus()).isEqualTo(EventStatus.TENTATIVE);
    assertThat(reloaded.getLastDeviceUpdate()).isEqualTo(deviceUpdate);
    assertThat(reloaded.getSyncHash()).hasSize(SHA256_BASE64_LENGTH).isEqualTo(syncHash);
    assertThat(reloaded.getStartDatetime()).isEqualTo(START);
    assertThat(reloaded.getEndDatetime()).isEqualTo(END);
    assertThat(reloaded.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("should reject a second live row when calendar and device event id are repeated")
  void shouldRejectSecondLiveRowWhenCalendarAndDeviceEventIdAreRepeated() {
    // Given: ya existe una fila viva del evento en el calendario
    ExternalCalendar calendar = calendar("duplicado@prueba.test");
    repository.saveAndFlush(event(calendar, DEVICE_EVENT_ID, EventStatus.CONFIRMED));

    // When / Then: uk_ce_calendar_device_live rechaza la segunda sincronizacion concurrente
    assertThatThrownBy(
            () -> repository.saveAndFlush(event(calendar, DEVICE_EVENT_ID, EventStatus.CONFIRMED)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should allow new live row when previous one is soft deleted")
  void shouldAllowNewLiveRowWhenPreviousOneIsSoftDeleted() {
    // Given: el evento se sincronizo y despues el calendario se desvinculo (borrado en suave)
    ExternalCalendar calendar = calendar("resync@prueba.test");
    ExternalEvent first = repository.saveAndFlush(event(calendar, DEVICE_EVENT_ID, EventStatus.CONFIRMED));
    repository.softDeleteByExternalCalendarIdAndDeviceEventId(calendar.getId(), DEVICE_EVENT_ID);
    em.clear();

    // When: se vuelve a vincular y a sincronizar el mismo evento
    ExternalEvent second =
        repository.saveAndFlush(event(calendar, DEVICE_EVENT_ID, EventStatus.CONFIRMED));
    em.clear();

    // Then: la fila borrada (is_live NULL) no choca y la busqueda solo ve la nueva
    assertThat(second.getId()).isNotEqualTo(first.getId());
    assertThat(repository.findByExternalCalendarIdAndDeviceEventId(calendar.getId(), DEVICE_EVENT_ID))
        .map(ExternalEvent::getId)
        .contains(second.getId());
    assertThat(repository.findByExternalCalendarId(calendar.getId())).hasSize(1);
  }

  @Test
  @DisplayName("should allow multiple rows when device event id is null")
  void shouldAllowMultipleRowsWhenDeviceEventIdIsNull() {
    // Given: dos eventos propios de la app, sin id de dispositivo, en el mismo calendario
    ExternalCalendar calendar = calendar("propios@prueba.test");

    // When
    repository.saveAndFlush(event(calendar, null, EventStatus.CONFIRMED));
    repository.saveAndFlush(event(calendar, null, EventStatus.CONFIRMED));
    em.clear();

    // Then: los NULL de la clave unica no chocan entre si
    assertThat(repository.findByExternalCalendarId(calendar.getId())).hasSize(2);
  }

  @Test
  @DisplayName("should allow same device event id when it belongs to two different calendars")
  void shouldAllowSameDeviceEventIdWhenItBelongsToTwoDifferentCalendars() {
    // Given
    User user = TestUsers.persist(em, "doscalendarios@prueba.test");
    ExternalCalendar first = TestCalendars.persist(em, user.getId());
    ExternalCalendar second = TestCalendars.persist(em, user.getId());

    // When
    repository.saveAndFlush(event(first, DEVICE_EVENT_ID, EventStatus.CONFIRMED));
    repository.saveAndFlush(event(second, DEVICE_EVENT_ID, EventStatus.CONFIRMED));
    em.clear();

    // Then: la unicidad es por (calendario, evento), no por evento
    assertThat(repository.findByExternalCalendarIdIn(List.of(first.getId(), second.getId())))
        .hasSize(2);
  }

  @Test
  @DisplayName("should exclude cancelled events when querying calendars by date range")
  void shouldExcludeCancelledEventsWhenQueryingCalendarsByDateRange() {
    // Given: dos eventos dentro del rango, uno confirmado y otro cancelado en el dispositivo
    ExternalCalendar calendar = calendar("rango@prueba.test");
    repository.saveAndFlush(event(calendar, "ev-confirmado", EventStatus.CONFIRMED));
    repository.saveAndFlush(event(calendar, "ev-cancelado", EventStatus.CANCELLED));
    em.clear();

    // When
    List<ExternalEvent> found =
        repository.findEventsByCalendarsAndDateRange(
            List.of(calendar.getId()), RANGE_START, RANGE_END);

    // Then: el literal 'CANCELLED' de la consulta coincide con lo que escribe @Enumerated(STRING)
    assertThat(found).extracting(ExternalEvent::getDeviceEventId).containsExactly("ev-confirmado");
  }

  private ExternalCalendar calendar(String email) {
    User user = TestUsers.persist(em, email);
    return TestCalendars.persist(em, user.getId());
  }

  private static ExternalEvent event(
      ExternalCalendar calendar, String deviceEventId, EventStatus status) {
    ExternalEvent e = new ExternalEvent();
    e.setExternalCalendarId(calendar.getId());
    e.setDeviceEventId(deviceEventId);
    e.setTitle("Evento");
    e.setStartDatetime(START);
    e.setEndDatetime(END);
    e.setStatus(status);
    return e;
  }
}
