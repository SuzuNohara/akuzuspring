package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nexus.dto.EmotionLogResponse;
import com.nexus.dto.LogEmotionRequest;
import com.nexus.entity.EmotionLog;
import com.nexus.entity.User;
import com.nexus.exception.ResourceNotFoundException;
import com.nexus.repository.EmotionLogRepository;
import com.nexus.repository.UserRepository;
import com.nexus.security.AesEncryptionService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * RF-31/RF-32: el servicio nunca debe guardar ni exponer valencia/activacion/etiqueta en claro --
 * siempre pasan por {@link AesEncryptionService}. RN-28/RNF-11 se verifican aqui; la fortaleza
 * del propio cifrado la cubre {@code AesEncryptionServiceTest}.
 */
class EmotionServiceTest {

    private static final long USER_ID = 4L;

    private final EmotionLogRepository emotionLogRepository = mock(EmotionLogRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AesEncryptionService aesEncryptionService = mock(AesEncryptionService.class);

    /** 23:30 del 6 de octubre en la CDMX (UTC-6) = 05:30 del 7 de octubre en UTC. */
    private static final Instant NOW_CDMX_LATE_NIGHT = Instant.parse("2026-10-07T05:30:00Z");

    private static final ZoneId CDMX = ZoneId.of("America/Mexico_City");

    private final EmotionService service =
            new EmotionService(
                    emotionLogRepository,
                    userRepository,
                    aesEncryptionService,
                    Clock.fixed(NOW_CDMX_LATE_NIGHT, CDMX));

    private void givenAUserAndEncryption() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(User.builder().id(USER_ID).build()));
        given(aesEncryptionService.encrypt(anyString())).willReturn("CIFRADO-NUEVO");
        given(emotionLogRepository.save(any(EmotionLog.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("should replace the log already registered today instead of adding a second one (RN-38)")
    void shouldReplaceTheLogAlreadyRegisteredTodayInsteadOfAddingASecondOne() {
        givenAUserAndEncryption();
        EmotionLog earlierToday = EmotionLog.builder().id(7L).encryptedPayload("CIFRADO-VIEJO").build();
        given(
                        emotionLogRepository.findByUserIdAndLoggedAtGreaterThanEqualAndLoggedAtLessThan(
                                eq(USER_ID), any(Instant.class), any(Instant.class)))
                .willReturn(List.of(earlierToday));

        service.logEmotion(USER_ID, LogEmotionRequest.builder().valence(0.5).activation(-0.5).build());

        InOrder order = inOrder(emotionLogRepository);
        order.verify(emotionLogRepository).deleteAll(List.of(earlierToday));
        order.verify(emotionLogRepository).save(any(EmotionLog.class));
    }

    @Test
    @DisplayName("should not delete anything on the first log of the day")
    void shouldNotDeleteAnythingOnTheFirstLogOfTheDay() {
        givenAUserAndEncryption();
        given(
                        emotionLogRepository.findByUserIdAndLoggedAtGreaterThanEqualAndLoggedAtLessThan(
                                eq(USER_ID), any(Instant.class), any(Instant.class)))
                .willReturn(List.of());

        service.logEmotion(USER_ID, LogEmotionRequest.builder().valence(0.5).activation(-0.5).build());

        verify(emotionLogRepository, never()).deleteAll(any());
        verify(emotionLogRepository).save(any(EmotionLog.class));
    }

    @Test
    @DisplayName("should use the Mexico City calendar day, not the UTC day (RN-38)")
    void shouldUseTheMexicoCityCalendarDayNotTheUtcDay() {
        givenAUserAndEncryption();
        given(
                        emotionLogRepository.findByUserIdAndLoggedAtGreaterThanEqualAndLoggedAtLessThan(
                                eq(USER_ID), any(Instant.class), any(Instant.class)))
                .willReturn(List.of());

        service.logEmotion(USER_ID, LogEmotionRequest.builder().valence(0.5).activation(-0.5).build());

        // A las 23:30 del 6 de octubre en la CDMX, "hoy" es el 6, no el 7 (que es lo que diria UTC).
        verify(emotionLogRepository)
                .findByUserIdAndLoggedAtGreaterThanEqualAndLoggedAtLessThan(
                        USER_ID,
                        Instant.parse("2026-10-06T06:00:00Z"),
                        Instant.parse("2026-10-07T06:00:00Z"));
    }

    @Test
    @DisplayName("should throw resource not found when the user does not exist")
    void shouldThrowResourceNotFoundWhenTheUserDoesNotExist() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.logEmotion(
                                        USER_ID,
                                        LogEmotionRequest.builder().valence(0.5).activation(0.2).build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("should persist the payload encrypted, never in plaintext")
    void shouldPersistThePayloadEncryptedNeverInPlaintext() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(User.builder().id(USER_ID).build()));
        given(aesEncryptionService.encrypt(anyString())).willReturn("CIFRADO-XYZ");
        given(emotionLogRepository.save(any(EmotionLog.class)))
                .willAnswer(
                        invocation -> {
                            EmotionLog arg = invocation.getArgument(0);
                            arg.setId(1L);
                            arg.setLoggedAt(Instant.parse("2026-10-06T12:00:00Z"));
                            return arg;
                        });

        EmotionLogResponse response =
                service.logEmotion(
                        USER_ID,
                        LogEmotionRequest.builder().valence(0.7).activation(-0.3).label("Feliz").build());

        var captor = org.mockito.ArgumentCaptor.forClass(EmotionLog.class);
        verify(emotionLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedPayload()).isEqualTo("CIFRADO-XYZ");
        assertThat(captor.getValue().getEncryptedPayload()).doesNotContain("0.7", "Feliz");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getValence()).isEqualTo(0.7);
        assertThat(response.getActivation()).isEqualTo(-0.3);
        assertThat(response.getLabel()).isEqualTo("Feliz");
        assertThat(response.getLoggedAt()).isEqualTo(Instant.parse("2026-10-06T12:00:00Z"));
    }

    @Test
    @DisplayName("should decrypt each entry when reading the history, most recent first")
    void shouldDecryptEachEntryWhenReadingTheHistoryMostRecentFirst() {
        EmotionLog newest =
                EmotionLog.builder()
                        .id(2L)
                        .encryptedPayload("CIFRADO-2")
                        .loggedAt(Instant.parse("2026-10-06T12:00:00Z"))
                        .build();
        EmotionLog oldest =
                EmotionLog.builder()
                        .id(1L)
                        .encryptedPayload("CIFRADO-1")
                        .loggedAt(Instant.parse("2026-10-05T12:00:00Z"))
                        .build();
        given(emotionLogRepository.findByUserIdOrderByLoggedAtDesc(USER_ID))
                .willReturn(List.of(newest, oldest));
        given(aesEncryptionService.decrypt("CIFRADO-2"))
                .willReturn("{\"valence\":0.9,\"activation\":0.8,\"label\":\"Emocionado\"}");
        given(aesEncryptionService.decrypt("CIFRADO-1"))
                .willReturn("{\"valence\":-0.4,\"activation\":-0.2,\"label\":\"Triste\"}");

        List<EmotionLogResponse> history = service.getHistory(USER_ID);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getId()).isEqualTo(2L);
        assertThat(history.get(0).getValence()).isEqualTo(0.9);
        assertThat(history.get(0).getLabel()).isEqualTo("Emocionado");
        assertThat(history.get(1).getId()).isEqualTo(1L);
        assertThat(history.get(1).getLabel()).isEqualTo("Triste");
    }
}
