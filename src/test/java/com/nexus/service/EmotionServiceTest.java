package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    private final EmotionService service =
            new EmotionService(emotionLogRepository, userRepository, aesEncryptionService);

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
