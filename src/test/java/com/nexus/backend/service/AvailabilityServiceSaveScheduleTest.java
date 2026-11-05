package com.nexus.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nexus.entity.AvailabilitySchedule;
import com.nexus.repository.AvailabilityScheduleRepository;
import com.nexus.repository.EventExceptionRepository;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.ExternalEventRepository;
import com.nexus.repository.UserRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** [primeros-pasos] R-03: guardar los horarios deja constancia en users.schedule_customized_at. */
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceSaveScheduleTest {

    private static final long USER = 13L;

    @Mock private AvailabilityScheduleRepository schedules;
    @Mock private EventRepository events;
    @Mock private ExternalCalendarRepository calendars;
    @Mock private ExternalEventRepository externalEvents;
    @Mock private EventExceptionRepository eventExceptions;
    @Mock private UserRepository users;
    @InjectMocks private AvailabilityService service;

    @Test
    @DisplayName("should mark the schedule as customized after saving the rows")
    void shouldMarkScheduleAsCustomizedAfterSavingRows() {
        List<AvailabilitySchedule> rows = List.of(AvailabilitySchedule.builder()
                .dayOfWeek(AvailabilitySchedule.DayOfWeek.MONDAY)
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(20, 0)).isEnabled(true).build());
        given(schedules.saveAll(anyList())).willReturn(rows);
        Instant before = Instant.now();

        service.saveUserSchedule(USER, rows);

        InOrder order = inOrder(schedules, users);
        order.verify(schedules).deleteByUserId(USER);
        order.verify(schedules).saveAll(rows);
        ArgumentCaptor<Instant> at = ArgumentCaptor.forClass(Instant.class);
        order.verify(users).markScheduleCustomized(eq(USER), at.capture());
        assertThat(at.getValue()).isBetween(before, Instant.now());
    }

    @Test
    @DisplayName("should not mark anything when saving the rows fails")
    void shouldNotMarkWhenSavingFails() {
        given(schedules.saveAll(anyList())).willThrow(new IllegalStateException("BD caida"));

        assertThatThrownBy(() -> service.saveUserSchedule(USER, List.of()))
                .isInstanceOf(IllegalStateException.class);

        verify(users, never()).markScheduleCustomized(any(), any());
    }
}
