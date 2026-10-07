package com.nexus.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-31/RF-32 - Registro e historial de estado emocional.
 *
 * <p>valencia, activacion y etiqueta nunca tocan la base de datos en claro: se serializan juntos
 * como JSON y se cifran con {@link AesEncryptionService} (RNF-11) antes de guardarse. RN-28 (nadie
 * mas que el propio usuario los ve) lo impone {@code EmotionController} vía
 * {@code OwnershipGuard.requireSelf} -- este servicio no conoce a la pareja vinculada en absoluto.
 */
@Service
@RequiredArgsConstructor
public class EmotionService {

    private final EmotionLogRepository emotionLogRepository;
    private final UserRepository userRepository;
    private final AesEncryptionService aesEncryptionService;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * RN-38: un solo registro por dia (dia de la CDMX, segun {@link #clock}); registrar de nuevo el
     * mismo dia reemplaza el anterior.
     */
    @Transactional
    public EmotionLogResponse logEmotion(Long userId, LogEmotionRequest request) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        EmotionPayload payload =
                new EmotionPayload(request.getValence(), request.getActivation(), request.getLabel());
        String encrypted = aesEncryptionService.encrypt(toJson(payload));

        LocalDate today = LocalDate.now(clock);
        List<EmotionLog> alreadyToday =
                emotionLogRepository.findByUserIdAndLoggedAtGreaterThanEqualAndLoggedAtLessThan(
                        userId,
                        today.atStartOfDay(clock.getZone()).toInstant(),
                        today.plusDays(1).atStartOfDay(clock.getZone()).toInstant());
        if (!alreadyToday.isEmpty()) {
            emotionLogRepository.deleteAll(alreadyToday);
        }

        EmotionLog saved =
                emotionLogRepository.save(
                        EmotionLog.builder().user(user).encryptedPayload(encrypted).build());

        return toResponse(saved.getId(), payload, saved.getLoggedAt());
    }

    public List<EmotionLogResponse> getHistory(Long userId) {
        return emotionLogRepository.findByUserIdOrderByLoggedAtDesc(userId).stream()
                .map(
                        log -> {
                            EmotionPayload payload =
                                    fromJson(aesEncryptionService.decrypt(log.getEncryptedPayload()));
                            return toResponse(log.getId(), payload, log.getLoggedAt());
                        })
                .toList();
    }

    private EmotionLogResponse toResponse(Long id, EmotionPayload payload, Instant loggedAt) {
        return EmotionLogResponse.builder()
                .id(id)
                .valence(payload.valence())
                .activation(payload.activation())
                .label(payload.label())
                .loggedAt(loggedAt)
                .build();
    }

    private String toJson(EmotionPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error al serializar el registro emocional", e);
        }
    }

    private EmotionPayload fromJson(String json) {
        try {
            return objectMapper.readValue(json, EmotionPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error al deserializar el registro emocional", e);
        }
    }

    private record EmotionPayload(Double valence, Double activation, String label) {}
}
