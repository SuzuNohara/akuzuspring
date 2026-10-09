package com.nexus.service;

import com.nexus.dto.MoodDaySummary;
import com.nexus.dto.MoodEntryResponse;
import com.nexus.dto.MoodHistoryResponse;
import com.nexus.dto.MoodOptionResponse;
import com.nexus.dto.RecordMoodResponse;
import com.nexus.entity.MoodEntry;
import com.nexus.entity.MoodOption;
import com.nexus.entity.User;
import com.nexus.exception.BadRequestException;
import com.nexus.exception.ConflictException;
import com.nexus.exception.ResourceNotFoundException;
import com.nexus.repository.MoodEntryRepository;
import com.nexus.repository.MoodOptionRepository;
import com.nexus.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas del registro e historial de ánimo (nexus-EMO-01). Dueña de las transacciones y única
 * frontera donde se decide alta/reemplazo (RN-38), privacidad (RN-28) y el promedio interno de CU40.
 *
 * <p>El código del ánimo nunca se registra en logs: solo {@code userId} y {@code entryId} (RN-28,
 * RNF-11). El cifrado lo hace el {@code MoodCodeConverter} de forma transparente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MoodService {

    static final int SUMMARY_DAYS = 7;
    static final int DEFAULT_HISTORY_LIMIT = 60;
    static final int MAX_HISTORY_LIMIT = 200;

    private static final String UNKNOWN_EMOJI = "❔";

    private final MoodEntryRepository entries;
    private final MoodOptionRepository options;
    private final UserRepository users;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MoodOptionResponse> listOptions() {
        return options.findAllByOrderBySortOrderAsc().stream().map(MoodOptionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<MoodEntryResponse> findToday(Long userId) {
        requireUser(userId);
        Map<String, MoodOption> catalog = catalogByCode();
        return entries.findByUserIdAndEntryDate(userId, today()).map(e -> toResponse(e, catalog));
    }

    @Transactional
    public RecordMoodResponse recordToday(Long userId, String code) {
        User user = requireUser(userId);
        Map<String, MoodOption> catalog = catalogByCode();
        if (!catalog.containsKey(code)) {
            throw new BadRequestException("Opción de ánimo no válida");
        }
        LocalDate day = today();
        Optional<MoodEntry> existing = entries.findByUserIdAndEntryDate(userId, day);
        MoodEntry entry;
        boolean replaced;
        if (existing.isPresent()) {
            entry = existing.get();
            entry.setMoodCode(code);
            replaced = true;
        } else {
            entry = MoodEntry.builder().user(user).entryDate(day).moodCode(code).build();
            replaced = false;
        }
        try {
            entry = entries.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ya existe un registro de hoy; recarga e inténtalo de nuevo");
        }
        log.info("Ánimo registrado userId={} entryId={} replaced={}", userId, entry.getId(), replaced);
        return new RecordMoodResponse(toResponse(entry, catalog), replaced);
    }

    @Transactional(readOnly = true)
    public MoodHistoryResponse history(Long userId, Optional<LocalDate> before, int limit) {
        requireUser(userId);
        if (limit < 1 || limit > MAX_HISTORY_LIMIT) {
            throw new BadRequestException("El límite debe estar entre 1 y " + MAX_HISTORY_LIMIT);
        }
        LocalDate day = today();
        Map<String, MoodOption> catalog = catalogByCode();

        LocalDate cursor = before.orElse(day.plusDays(1));
        List<MoodEntry> page =
                entries.findPageBefore(userId, cursor, PageRequest.of(0, limit + 1));
        LocalDate nextBefore = null;
        if (page.size() > limit) {
            page = page.subList(0, limit);
            nextBefore = page.get(page.size() - 1).getEntryDate();
        }
        List<MoodEntryResponse> entryResponses =
                page.stream().map(e -> toResponse(e, catalog)).toList();

        List<MoodDaySummary> last7Days = buildSummary(userId, day, catalog);

        return new MoodHistoryResponse(day, last7Days, entryResponses, nextBefore);
    }

    /**
     * Promedio del ánimo de los últimos 7 días de un usuario, para CU40 (RN-28). Uso interno: NUNCA
     * se expone por HTTP.
     */
    @Transactional(readOnly = true)
    public Optional<MoodAverage> averageLast7Days(Long userId) {
        LocalDate day = today();
        Map<String, MoodOption> catalog = catalogByCode();
        List<MoodEntry> window =
                entries.findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                        userId, day.minusDays(SUMMARY_DAYS - 1), day);
        double valenceSum = 0;
        double arousalSum = 0;
        int days = 0;
        for (MoodEntry e : window) {
            MoodOption option = catalog.get(e.getMoodCode());
            if (option == null) {
                log.warn("Código de ánimo desconocido fuera del promedio entryId={}", e.getId());
                continue;
            }
            valenceSum += option.getValence().doubleValue();
            arousalSum += option.getArousal().doubleValue();
            days++;
        }
        if (days == 0) {
            return Optional.empty();
        }
        return Optional.of(new MoodAverage(valenceSum / days, arousalSum / days, days));
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    private Map<String, MoodOption> catalogByCode() {
        Map<String, MoodOption> byCode = new LinkedHashMap<>();
        for (MoodOption o : options.findAllByOrderBySortOrderAsc()) {
            byCode.put(o.getCode(), o);
        }
        return byCode;
    }

    private User requireUser(Long userId) {
        return users.findActiveById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private List<MoodDaySummary> buildSummary(
            Long userId, LocalDate today, Map<String, MoodOption> catalog) {
        Map<LocalDate, MoodEntry> byDate = new LinkedHashMap<>();
        for (MoodEntry e :
                entries.findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                        userId, today.minusDays(SUMMARY_DAYS - 1), today)) {
            byDate.put(e.getEntryDate(), e);
        }
        List<MoodDaySummary> summary = new ArrayList<>(SUMMARY_DAYS);
        for (int i = 0; i < SUMMARY_DAYS; i++) {
            LocalDate date = today.minusDays(i);
            MoodEntry e = byDate.get(date);
            if (e == null) {
                summary.add(new MoodDaySummary(date, null, null));
            } else {
                MoodOption option = catalog.get(e.getMoodCode());
                String emoji = option != null ? option.getEmoji() : UNKNOWN_EMOJI;
                summary.add(new MoodDaySummary(date, e.getMoodCode(), emoji));
            }
        }
        return summary;
    }

    private MoodEntryResponse toResponse(MoodEntry entry, Map<String, MoodOption> catalog) {
        String code = entry.getMoodCode();
        MoodOption option = catalog.get(code);
        String label = option != null ? option.getLabel() : code;
        String emoji = option != null ? option.getEmoji() : UNKNOWN_EMOJI;
        return new MoodEntryResponse(entry.getEntryDate(), code, label, emoji, entry.getUpdatedAt());
    }
}
