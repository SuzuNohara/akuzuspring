package com.nexus.controller;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.backend.service.AvailabilityService;
import com.nexus.config.SecurityConfig;
import com.nexus.dto.AvailabilityScheduleDTO;
import com.nexus.entity.AvailabilitySchedule;
import com.nexus.entity.User;
import com.nexus.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de la superficie HTTP de {@link AvailabilityController} en lo que toca a la guarda de
 * dia repetido de {@code PUT /availability/schedule/{userId}} (nexus-SCHEMA-01, D10).
 *
 * <p>{@link AvailabilityService} y {@link UserRepository} van mockeados: aqui se prueba el
 * cableado del controlador, no la persistencia. La tabla la cubren los {@code *IT}.
 */
@WebMvcTest(AvailabilityController.class)
@Import(SecurityConfig.class)
class AvailabilityControllerTest {

    private static final long USER_ID = 4L;

    private static final String SCHEDULE_PATH = "/availability/schedule/" + USER_ID;

    private static final String MONDAY_ROW =
            "{\"day\":\"MONDAY\",\"enabled\":true,\"startTime\":\"09:00\",\"endTime\":\"18:00\"}";

    private static final String TUESDAY_ROW =
            "{\"day\":\"TUESDAY\",\"enabled\":true,\"startTime\":\"09:00\",\"endTime\":\"18:00\"}";

    private static final String DUPLICATE_DAY_BODY = "[" + MONDAY_ROW + "," + MONDAY_ROW + "]";

    private static final String DISTINCT_DAYS_BODY = "[" + MONDAY_ROW + "," + TUESDAY_ROW + "]";

    @Autowired private MockMvc mockMvc;

    @MockBean private AvailabilityService availabilityService;

    @MockBean private UserRepository userRepository;

    @Test
    @DisplayName("should return 400 with the day and not touch the service when a day is repeated")
    void shouldReturn400WithTheDayAndNotTouchTheServiceWhenADayIsRepeated() throws Exception {
        // Given: el usuario existe y el cuerpo trae MONDAY dos veces
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(existingUser()));

        // When / Then: 400 con el dia repetido en el cuerpo
        mockMvc.perform(
                        put(SCHEDULE_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(DUPLICATE_DAY_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.day").value("MONDAY"));

        // Then: la peticion no llega al servicio
        verifyNoInteractions(availabilityService);
    }

    @Test
    @DisplayName("should return 200 and pass two rows to the service when days are distinct")
    void shouldReturn200AndPassTwoRowsToTheServiceWhenDaysAreDistinct() throws Exception {
        // Given: el usuario existe y el cuerpo trae MONDAY y TUESDAY
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(existingUser()));
        given(availabilityService.saveUserSchedule(eq(USER_ID), anyRows()))
                .willReturn(List.of(new AvailabilityScheduleDTO(), new AvailabilityScheduleDTO()));

        // When / Then: 200
        mockMvc.perform(
                        put(SCHEDULE_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(DISTINCT_DAYS_BODY))
                .andExpect(status().isOk());

        // Then: el servicio recibe exactamente dos filas
        then(availabilityService).should().saveUserSchedule(eq(USER_ID), twoRows());
    }

    private static User existingUser() {
        return User.builder()
                .id(USER_ID)
                .email("a@b.co")
                .emailConfirmed(true)
                .displayName("Ada")
                .linkCode("ABCD-1234")
                .build();
    }

    private static List<AvailabilitySchedule> anyRows() {
        return argThat(rows -> true);
    }

    private static List<AvailabilitySchedule> twoRows() {
        return argThat(rows -> rows != null && rows.size() == 2);
    }
}
