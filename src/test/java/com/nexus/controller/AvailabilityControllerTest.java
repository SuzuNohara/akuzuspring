package com.nexus.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.backend.service.AvailabilityService;
import com.nexus.config.SecurityConfig;
import com.nexus.dto.AvailabilityScheduleDTO;
import com.nexus.entity.AvailabilitySchedule;
import com.nexus.entity.User;
import com.nexus.repository.UserRepository;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import java.util.List;
import java.util.Map;
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
 * Pruebas de la superficie HTTP de {@link AvailabilityController}: la guarda de dia repetido de
 * {@code PUT /availability/schedule/{userId}} (nexus-SCHEMA-01, D10) y las guardas de pertenencia
 * de nexus-SEC-04 (el {@code {userId}} de la URL debe coincidir con el usuario autenticado, salvo
 * en {@code /mutual}, donde basta ser uno de los dos participantes).
 *
 * <p>{@link AvailabilityService}, {@link UserRepository} y {@link JwtService} van mockeados: aqui
 * se prueba el cableado del controlador, no la persistencia ni la firma del token. La tabla la
 * cubren los {@code *IT}; la firma, {@code JwtServiceTest}.
 */
@WebMvcTest(AvailabilityController.class)
@Import(SecurityConfig.class)
class AvailabilityControllerTest {

    private static final long USER_ID = 4L;

    private static final long OTHER_USER_ID = 9L;

    private static final String USER_EMAIL = "a@b.co";

    private static final String BEARER = "Bearer test-token";

    private static final String SCHEDULE_PATH = "/availability/schedule/" + USER_ID;

    private static final String CALCULATE_PATH = "/availability/calculate/" + USER_ID;

    private static final String MUTUAL_PATH =
            "/availability/mutual/" + USER_ID + "/" + OTHER_USER_ID;

    private static final String MONDAY_ROW =
            "{\"day\":\"MONDAY\",\"enabled\":true,\"startTime\":\"09:00\",\"endTime\":\"18:00\"}";

    private static final String TUESDAY_ROW =
            "{\"day\":\"TUESDAY\",\"enabled\":true,\"startTime\":\"09:00\",\"endTime\":\"18:00\"}";

    private static final String DUPLICATE_DAY_BODY = "[" + MONDAY_ROW + "," + MONDAY_ROW + "]";

    private static final String DISTINCT_DAYS_BODY = "[" + MONDAY_ROW + "," + TUESDAY_ROW + "]";

    @Autowired private MockMvc mockMvc;

    @MockBean private AvailabilityService availabilityService;

    @MockBean private UserRepository userRepository;

    @MockBean private JwtService jwtService;

    private void authenticateAs(long userId) {
        given(jwtService.parse(anyString())).willReturn(new AuthenticatedUser(userId, USER_EMAIL));
    }

    @Test
    @DisplayName("should return 400 with the day and not touch the service when a day is repeated")
    void shouldReturn400WithTheDayAndNotTouchTheServiceWhenADayIsRepeated() throws Exception {
        // Given: el usuario esta autenticado como el dueno del recurso y el cuerpo trae MONDAY dos veces
        authenticateAs(USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(existingUser()));

        // When / Then: 400 con el dia repetido en el cuerpo
        mockMvc.perform(
                        put(SCHEDULE_PATH)
                                .header("Authorization", BEARER)
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
        // Given: el usuario esta autenticado como el dueno del recurso y el cuerpo trae MONDAY y TUESDAY
        authenticateAs(USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(existingUser()));
        given(availabilityService.saveUserSchedule(eq(USER_ID), anyRows()))
                .willReturn(List.of(new AvailabilityScheduleDTO(), new AvailabilityScheduleDTO()));

        // When / Then: 200
        mockMvc.perform(
                        put(SCHEDULE_PATH)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(DISTINCT_DAYS_BODY))
                .andExpect(status().isOk());

        // Then: el servicio recibe exactamente dos filas
        then(availabilityService).should().saveUserSchedule(eq(USER_ID), twoRows());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when saving another user's schedule")
    void shouldReturn403AndNotTouchTheServiceWhenSavingAnotherUsersSchedule() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        put(SCHEDULE_PATH)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(DISTINCT_DAYS_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(availabilityService);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when reading another user's schedule")
    void shouldReturn403AndNotTouchTheServiceWhenReadingAnotherUsersSchedule() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(get(SCHEDULE_PATH).header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(availabilityService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when calculating another user's slots")
    void shouldReturn403AndNotTouchTheServiceWhenCalculatingAnotherUsersSlots() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(get(CALCULATE_PATH).header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(availabilityService);
    }

    @Test
    @DisplayName("should return 200 when the first participant requests mutual availability")
    void shouldReturn200WhenTheFirstParticipantRequestsMutualAvailability() throws Exception {
        authenticateAs(USER_ID);
        given(availabilityService.findMutualAvailability(USER_ID, OTHER_USER_ID, 7))
                .willReturn(Map.of("slots", List.of()));

        mockMvc.perform(get(MUTUAL_PATH).header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 200 when the second participant requests mutual availability")
    void shouldReturn200WhenTheSecondParticipantRequestsMutualAvailability() throws Exception {
        authenticateAs(OTHER_USER_ID);
        given(availabilityService.findMutualAvailability(USER_ID, OTHER_USER_ID, 7))
                .willReturn(Map.of("slots", List.of()));

        mockMvc.perform(get(MUTUAL_PATH).header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when neither id is the authenticated user")
    void shouldReturn403AndNotTouchTheServiceWhenNeitherIdIsTheAuthenticatedUser() throws Exception {
        authenticateAs(999L);

        mockMvc.perform(get(MUTUAL_PATH).header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(availabilityService);
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
