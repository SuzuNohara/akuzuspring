package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexus.dto.SyncEventRequest;
import com.nexus.entity.Event;
import com.nexus.entity.ExternalEvent;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.ExternalEventRepository;
import com.nexus.service.push.NoticeTypes;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * [avisos-calendario] RF-24 - Solo es conflicto si cambio en el telefono la copia de un evento
 * creado en Nexus; se avisa una sola vez por cambio.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExternalCalendarConflictTest {

    private static final long ME = 13L;
    private static final long NEXUS_ID = 21L;
    /** Sabado 31 de octubre de 2026, 20:00 a 22:00 en la CDMX. */
    private static final Instant START = Instant.parse("2026-11-01T02:00:00Z");
    private static final Instant END = Instant.parse("2026-11-01T04:00:00Z");

    @Mock private ExternalCalendarRepository calendars;
    @Mock private ExternalEventRepository externalEvents;
    @Mock private EventRepository events;
    @Mock private AppNotificationService appNotifications;
    @InjectMocks private ExternalCalendarService service;

    private ExternalEvent copy;

    @BeforeEach
    void setUp() {
        copy = new ExternalEvent();
        copy.setExternalCalendarId(1L);
        copy.setDeviceEventId("dev-7");
        copy.setAppEventId(NEXUS_ID);
        // [sync-calendario] Lo ultimo que se guardo de la copia: igual a Nexus.
        copy.setTitle("Cena");
        copy.setStartDatetime(START);
        copy.setEndDatetime(END);
        when(externalEvents.findByExternalCalendarIdAndDeviceEventId(1L, "dev-7")).thenReturn(Optional.of(copy));
        nexus = Event.builder().id(NEXUS_ID).title("Cena").startDateTime(START).endDateTime(END).build();
        when(events.findById(NEXUS_ID)).thenReturn(Optional.of(nexus));
    }

    private Event nexus;

    private static SyncEventRequest fromPhone(String title, Instant start, Instant end) {
        SyncEventRequest r = new SyncEventRequest();
        r.setExternalCalendarId(1L);
        r.setDeviceEventId("dev-7");
        r.setTitle(title);
        r.setStartDatetime(start);
        r.setEndDatetime(end);
        r.setIsAllDay(false);
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<String> sync(SyncEventRequest r) {
        return (List<String>) service.syncEvents(ME, List.of(r)).get("conflicts");
    }

    @Test
    @DisplayName("un evento personal (no salio de Nexus) nunca es conflicto, aunque cambie todo")
    void personalEventNeverConflicts() {
        copy.setAppEventId(null);

        assertThat(sync(fromPhone("Ejercicio", START.plusSeconds(3600), END.plusSeconds(7200)))).isEmpty();
        verify(appNotifications, never()).notify(anyLong(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("la copia igual a Nexus no es conflicto")
    void sameAsNexusIsNotAConflict() {
        assertThat(sync(fromPhone("Cena", START, END))).isEmpty();
        verify(appNotifications, never()).notify(anyLong(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("la copia de un evento que ya no existe en Nexus se ignora")
    void deletedNexusEventIsIgnored() {
        when(events.findById(NEXUS_ID)).thenReturn(Optional.empty());

        assertThat(sync(fromPhone("Cena", START.plusSeconds(3600), END))).isEmpty();
    }

    @Test
    @DisplayName("si cambia la hora en el telefono avisa una vez, con lo que sigue en Nexus")
    void changedTimeNotifiesOnce() {
        List<String> conflicts = sync(fromPhone("Cena", START.plusSeconds(3600), END.plusSeconds(3600)));

        assertThat(conflicts).containsExactly("Cena");
        verify(appNotifications).notify(eq(ME), eq(null), eq(NoticeTypes.SYNC_CONFLICT),
                eq("Cambió un evento en tu calendario"),
                eq("\"Cena\" cambió en tu calendario del teléfono. En Nexus sigue el sábado 31 de 20:00 a 22:00."),
                eq(Map.of("eventId", "21")));
    }

    @Test
    @DisplayName("el mismo cambio en la siguiente sincronizacion no vuelve a avisar; uno nuevo si")
    void sameChangeIsNotRepeated() {
        sync(fromPhone("Cena", START.plusSeconds(3600), END));
        assertThat(sync(fromPhone("Cena", START.plusSeconds(3600), END))).isEmpty();
        sync(fromPhone("Cena con Sam", START.plusSeconds(3600), END));

        verify(appNotifications, times(2)).notify(anyLong(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("si la copia vuelve a coincidir se borra la huella, y un cambio posterior vuelve a avisar")
    void backInSyncClearsTheHash() {
        sync(fromPhone("Cena", START.plusSeconds(3600), END));
        sync(fromPhone("Cena", START, END));
        assertThat(copy.getConflictHash()).isNull();

        sync(fromPhone("Cena", START.plusSeconds(3600), END));
        verify(appNotifications, times(2)).notify(anyLong(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("segundos y espacios de mas no cuentan como cambio")
    void secondsAndSpacesAreNotChanges() {
        assertThat(sync(fromPhone("  Cena  ", START.plusSeconds(30), END.plusSeconds(59)))).isEmpty();
    }

    // --- [sync-calendario] U3: se compara contra la copia guardada ------------------------------

    @Test
    @DisplayName("[sync-calendario] si cambio Nexus y el telefono trae lo guardado, no avisa")
    void nexusChangedIsNotAConflict() {
        nexus.setStartDateTime(START.plusSeconds(3600));
        nexus.setEndDateTime(END.plusSeconds(3600));

        assertThat(sync(fromPhone("Cena", START, END))).isEmpty();
        verify(appNotifications, never()).notify(anyLong(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("[sync-calendario] si en el telefono la dejan igual que Nexus, no avisa")
    void phoneMatchesNexusAgain() {
        nexus.setTitle("Cena con Sam");

        assertThat(sync(fromPhone("Cena con Sam", START, END))).isEmpty();
    }

    @Test
    @DisplayName("[sync-calendario] lo que escribe Nexus se guarda con su firma, sin avisar")
    void fromNexusSavesTheSignature() {
        copy.setConflictHash("huella-vieja");
        nexus.setTitle("Cena con Sam");
        SyncEventRequest written = fromPhone("Cena con Sam", START, END);
        written.setAppEventId(NEXUS_ID);
        written.setFromNexus(true);

        assertThat(sync(written)).isEmpty();
        assertThat(copy.getTitle()).isEqualTo("Cena con Sam");
        assertThat(copy.getExportSignature()).isEqualTo(CalendarCopyPlanner.signatureOf(nexus));
        assertThat(copy.getConflictHash()).isNull();
        verify(appNotifications, never()).notify(anyLong(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("[sync-calendario] una copia nueva escrita por Nexus guarda su firma")
    void newCopyFromNexusSavesTheSignature() {
        when(externalEvents.findByExternalCalendarIdAndDeviceEventId(1L, "dev-7")).thenReturn(Optional.empty());
        SyncEventRequest written = fromPhone("Cena", START, END);
        written.setAppEventId(NEXUS_ID);
        written.setFromNexus(true);
        org.mockito.ArgumentCaptor<ExternalEvent> saved = org.mockito.ArgumentCaptor.forClass(ExternalEvent.class);

        sync(written);

        verify(externalEvents).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getExportSignature()).isEqualTo(CalendarCopyPlanner.signatureOf(nexus));
    }

    @Test
    @DisplayName("[sync-calendario] de una serie llega otra ocurrencia a la misma hora: no avisa")
    void seriesOccurrenceIsNotAConflict() {
        SyncEventRequest week3 = fromPhone("Cena", START.plusSeconds(14 * 86400), END.plusSeconds(14 * 86400));
        week3.setRecurrenceRule("FREQ=WEEKLY;BYDAY=SA");

        assertThat(sync(week3)).isEmpty();
    }

    @Test
    @DisplayName("[sync-calendario] de una serie cambio la hora en el telefono: avisa")
    void seriesNewTimeIsAConflict() {
        SyncEventRequest moved = fromPhone("Cena", START.plusSeconds(14 * 86400 + 3600), END.plusSeconds(14 * 86400 + 3600));
        moved.setRecurrenceRule("FREQ=WEEKLY;BYDAY=SA");

        assertThat(sync(moved)).containsExactly("Cena");
    }
}
