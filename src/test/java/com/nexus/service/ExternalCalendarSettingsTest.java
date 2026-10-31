package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexus.dto.ExternalCalendarDTO;
import com.nexus.entity.ExternalCalendar;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.ExternalEventRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** [sync-calendario] U1 - RF-22: un solo calendario destino por persona. */
@ExtendWith(MockitoExtension.class)
class ExternalCalendarSettingsTest {

    private static final long ME = 13L;

    @Mock private ExternalCalendarRepository calendars;
    @Mock private ExternalEventRepository externalEvents;
    @Mock private EventRepository events;
    @Mock private AppNotificationService appNotifications;
    @InjectMocks private ExternalCalendarService service;

    private static ExternalCalendar calendar(long id, String deviceId, boolean export, boolean sync) {
        ExternalCalendar c = new ExternalCalendar();
        c.setId(id);
        c.setUserId(ME);
        c.setDeviceCalendarId(deviceId);
        c.setCalendarName("Cal " + id);
        c.setExportEvents(export);
        c.setSyncEnabled(sync);
        return c;
    }

    private void mine(ExternalCalendar... all) {
        for (ExternalCalendar c : all) {
            lenient().when(calendars.findByUserIdAndDeviceCalendarId(ME, c.getDeviceCalendarId())).thenReturn(Optional.of(c));
        }
        lenient().when(calendars.findByUserIdAndIsActiveTrue(ME)).thenReturn(List.of(all));
        lenient().when(calendars.save(any(ExternalCalendar.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("prender el destino apaga los demas y prende su sincronizacion")
    void onlyOneTarget() {
        ExternalCalendar work = calendar(1L, "dev-1", true, true);
        ExternalCalendar home = calendar(2L, "dev-2", false, false);
        mine(work, home);

        ExternalCalendarDTO result = service.updateCalendarSettings(ME, "dev-2", null, null, true);

        assertThat(result.getExportEvents()).isTrue();
        assertThat(home.getExportEvents()).isTrue();
        assertThat(home.getSyncEnabled()).isTrue();
        assertThat(work.getExportEvents()).isFalse();
    }

    @Test
    @DisplayName("apagarlo deja todos apagados")
    void turnOff() {
        ExternalCalendar home = calendar(2L, "dev-2", true, true);
        mine(home);

        service.updateCalendarSettings(ME, "dev-2", null, null, false);

        assertThat(home.getExportEvents()).isFalse();
        assertThat(home.getSyncEnabled()).isTrue();
    }

    @Test
    @DisplayName("solo toca los calendarios de la misma persona")
    void onlyOwnCalendars() {
        mine(calendar(2L, "dev-2", false, true));

        service.updateCalendarSettings(ME, "dev-2", null, null, true);

        verify(calendars, never()).findByUserIdAndIsActiveTrue(org.mockito.ArgumentMatchers.longThat(id -> id != ME));
    }

    @Test
    @DisplayName("sin exportEvents no cambia el destino")
    void nullLeavesItAlone() {
        ExternalCalendar work = calendar(1L, "dev-1", true, true);
        ExternalCalendar home = calendar(2L, "dev-2", false, true);
        mine(work, home);

        service.updateCalendarSettings(ME, "dev-2", false, null, null);

        assertThat(work.getExportEvents()).isTrue();
        assertThat(home.getExportEvents()).isFalse();
        assertThat(home.getSyncEnabled()).isFalse();
        verify(calendars, never()).findByUserIdAndIsActiveTrue(anyLong());
    }
}
