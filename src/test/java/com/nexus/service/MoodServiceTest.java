package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Reglas de {@link MoodService} con repos mockeados y {@code Clock.fixed} (nexus-EMO-01). Prueba
 * alta/reemplazo (RN-38), día natural de la zona, validación, 409, privacidad de logs (RN-28),
 * historial con cursor y el promedio interno de CU40.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MoodServiceTest {

    private static final long USER_ID = 4L;
    private static final Instant NOON_UTC = Instant.parse("2026-10-08T18:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    @Mock private MoodEntryRepository entries;
    @Mock private MoodOptionRepository options;
    @Mock private UserRepository users;

    private MoodService service;

    @BeforeEach
    void setUp() {
        service = new MoodService(entries, options, users, fixedAt(NOON_UTC));
    }

    // --- T-26 / T-27 alta nueva --------------------------------------------------------------

    @Test
    @DisplayName("should create a new entry when the user has no entry today")
    void shouldCreateEntryWhenUserHasNoEntryToday() {
        givenUserExists();
        givenCatalog();
        given(entries.findByUserIdAndEntryDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(entries.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));

        RecordMoodResponse result = service.recordToday(USER_ID, "CALM");

        assertThat(result.replaced()).isFalse();
        assertThat(result.entry().code()).isEqualTo("CALM");
        assertThat(result.entry().date()).isEqualTo(TODAY);
        ArgumentCaptor<MoodEntry> saved = ArgumentCaptor.forClass(MoodEntry.class);
        then(entries).should().saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEntryDate()).isEqualTo(TODAY);
        assertThat(saved.getValue().getMoodCode()).isEqualTo("CALM");
    }

    @Test
    @DisplayName("should record a mood without consulting any link between partners")
    void shouldNotConsultLinksWhenRecording() {
        givenUserExists();
        givenCatalog();
        given(entries.findByUserIdAndEntryDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(entries.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));

        service.recordToday(USER_ID, "HAPPY");

        // El servicio no inyecta ningún repositorio de vínculo: registrar el ánimo propio no
        // requiere pareja (CU31). Solo toca entradas, catálogo y el usuario propio.
        then(users).should().findActiveById(USER_ID);
        then(entries).should().saveAndFlush(any());
    }

    // --- T-28 reemplazo y validación ---------------------------------------------------------

    @Test
    @DisplayName("should replace the same row when an entry already exists today")
    void shouldReplaceSameRowWhenEntryExistsToday() {
        givenUserExists();
        givenCatalog();
        MoodEntry existing =
                MoodEntry.builder().id(7L).user(user()).entryDate(TODAY).moodCode("SAD").build();
        given(entries.findByUserIdAndEntryDate(USER_ID, TODAY)).willReturn(Optional.of(existing));
        given(entries.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));

        RecordMoodResponse result = service.recordToday(USER_ID, "CALM");

        assertThat(result.replaced()).isTrue();
        ArgumentCaptor<MoodEntry> saved = ArgumentCaptor.forClass(MoodEntry.class);
        then(entries).should().saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(7L); // misma fila, no un INSERT nuevo
        assertThat(saved.getValue().getMoodCode()).isEqualTo("CALM");
    }

    @Test
    @DisplayName("should reject an unknown code without writing anything")
    void shouldRejectUnknownCodeWithoutWriting() {
        givenUserExists();
        givenCatalog();

        assertThatThrownBy(() -> service.recordToday(USER_ID, "UNKNOWN"))
                .isInstanceOf(BadRequestException.class);

        then(entries).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("should throw not found when the user does not exist")
    void shouldThrowNotFoundWhenUserDoesNotExist() {
        given(users.findActiveById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordToday(USER_ID, "CALM"))
                .isInstanceOf(ResourceNotFoundException.class);

        then(entries).shouldHaveNoInteractions();
    }

    // --- T-29 día natural de la zona ---------------------------------------------------------

    @Test
    @DisplayName("should assign the previous day when it is past midnight UTC but not in Mexico")
    void shouldAssignPreviousDayWhenUtcIsAfterMidnightButMexicoIsNot() {
        service = new MoodService(entries, options, users, fixedAt(Instant.parse("2026-10-09T05:30:00Z")));
        givenUserExists();
        givenCatalog();
        given(entries.findByUserIdAndEntryDate(eq(USER_ID), any())).willReturn(Optional.empty());
        given(entries.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));

        service.recordToday(USER_ID, "CALM");

        ArgumentCaptor<MoodEntry> saved = ArgumentCaptor.forClass(MoodEntry.class);
        then(entries).should().saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEntryDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    @DisplayName("should assign the new day once it is past midnight in Mexico")
    void shouldAssignNewDayAfterMexicoMidnight() {
        service = new MoodService(entries, options, users, fixedAt(Instant.parse("2026-10-09T06:30:00Z")));
        givenUserExists();
        givenCatalog();
        given(entries.findByUserIdAndEntryDate(eq(USER_ID), any())).willReturn(Optional.empty());
        given(entries.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));

        service.recordToday(USER_ID, "CALM");

        ArgumentCaptor<MoodEntry> saved = ArgumentCaptor.forClass(MoodEntry.class);
        then(entries).should().saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEntryDate()).isEqualTo(LocalDate.of(2026, 10, 9));
    }

    // --- T-30 conflicto ----------------------------------------------------------------------

    @Test
    @DisplayName("should throw conflict when a concurrent insert violates the unique key")
    void shouldThrowConflictWhenConcurrentInsertViolatesUniqueKey() {
        givenUserExists();
        givenCatalog();
        given(entries.findByUserIdAndEntryDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(entries.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("uk_mood_user_day"));

        assertThatThrownBy(() -> service.recordToday(USER_ID, "CALM"))
                .isInstanceOf(ConflictException.class);
    }

    // --- T-31 sin fugas en logs --------------------------------------------------------------

    @Test
    @DisplayName("should never log the mood code when recording")
    void shouldNeverLogMoodCodeWhenRecording(CapturedOutput out) {
        givenUserExists();
        givenCatalog();
        given(entries.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));
        given(entries.findByUserIdAndEntryDate(USER_ID, TODAY))
                .willReturn(Optional.empty())
                .willReturn(
                        Optional.of(
                                MoodEntry.builder()
                                        .id(1L)
                                        .user(user())
                                        .entryDate(TODAY)
                                        .moodCode("CALM")
                                        .build()));

        service.recordToday(USER_ID, "CALM");
        service.recordToday(USER_ID, "HAPPY");

        assertThat(out).doesNotContain("CALM").doesNotContain("HAPPY");
    }

    // --- T-32 catálogo y hoy -----------------------------------------------------------------

    @Test
    @DisplayName("should list options in sort order")
    void shouldListOptionsInSortOrder() {
        givenCatalog();

        List<MoodOptionResponse> list = service.listOptions();

        assertThat(list).extracting(MoodOptionResponse::code).containsExactly(
                "EXCITED", "HAPPY", "CALM", "TIRED", "SAD", "DOWN", "STRESSED", "SURPRISED");
    }

    @Test
    @DisplayName("should return empty when there is no entry today")
    void shouldReturnEmptyWhenNoEntryToday() {
        givenUserExists();
        givenCatalog();
        given(entries.findByUserIdAndEntryDate(USER_ID, TODAY)).willReturn(Optional.empty());

        assertThat(service.findToday(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("should map an unknown stored code to a placeholder when reading today")
    void shouldMapUnknownCodeToPlaceholderWhenReadingToday() {
        givenUserExists();
        givenCatalog();
        given(entries.findByUserIdAndEntryDate(USER_ID, TODAY))
                .willReturn(
                        Optional.of(
                                MoodEntry.builder()
                                        .id(3L)
                                        .user(user())
                                        .entryDate(TODAY)
                                        .moodCode("RETIRED")
                                        .build()));

        MoodEntryResponse response = service.findToday(USER_ID).orElseThrow();

        assertThat(response.code()).isEqualTo("RETIRED");
        assertThat(response.label()).isEqualTo("RETIRED");
        assertThat(response.emoji()).isEqualTo("❔");
    }

    // --- T-33 historial ----------------------------------------------------------------------

    @Test
    @DisplayName("should build a seven day summary with nulls for missing days")
    void shouldBuildSevenDaySummaryWithNullsForMissingDays() {
        givenUserExists();
        givenCatalog();
        given(entries.findPageBefore(eq(USER_ID), any(), any())).willReturn(List.of());
        given(
                        entries.findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                                USER_ID, TODAY.minusDays(6), TODAY))
                .willReturn(
                        List.of(
                                entry(TODAY, "CALM"),
                                entry(TODAY.minusDays(2), "HAPPY")));

        MoodHistoryResponse history = service.history(USER_ID, Optional.empty(), 60);

        assertThat(history.today()).isEqualTo(TODAY);
        assertThat(history.last7Days()).hasSize(7);
        assertThat(history.last7Days().get(0).date()).isEqualTo(TODAY);
        assertThat(history.last7Days().get(0).code()).isEqualTo("CALM");
        assertThat(history.last7Days().get(1).code()).isNull(); // ayer sin registro
        assertThat(history.last7Days().get(2).code()).isEqualTo("HAPPY");
        assertThat(history.last7Days().get(6).date()).isEqualTo(TODAY.minusDays(6));
    }

    @Test
    @DisplayName("should set nextBefore when more than the limit exist")
    void shouldSetNextBeforeWhenMoreThanLimitExist() {
        givenUserExists();
        givenCatalog();
        givenEmptyWindow();
        given(entries.findPageBefore(eq(USER_ID), any(), any()))
                .willReturn(
                        List.of(
                                entry(TODAY, "CALM"),
                                entry(TODAY.minusDays(1), "HAPPY"),
                                entry(TODAY.minusDays(2), "SAD")));

        MoodHistoryResponse history = service.history(USER_ID, Optional.empty(), 2);

        assertThat(history.entries()).hasSize(2);
        assertThat(history.nextBefore()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    @DisplayName("should leave nextBefore null when there are no more")
    void shouldLeaveNextBeforeNullWhenNoMore() {
        givenUserExists();
        givenCatalog();
        givenEmptyWindow();
        given(entries.findPageBefore(eq(USER_ID), any(), any()))
                .willReturn(List.of(entry(TODAY, "CALM")));

        MoodHistoryResponse history = service.history(USER_ID, Optional.empty(), 60);

        assertThat(history.entries()).hasSize(1);
        assertThat(history.nextBefore()).isNull();
    }

    @Test
    @DisplayName("should default the cursor to tomorrow when no before is given")
    void shouldDefaultCursorToTomorrow() {
        givenUserExists();
        givenCatalog();
        givenEmptyWindow();
        given(entries.findPageBefore(eq(USER_ID), any(), any())).willReturn(List.of());

        service.history(USER_ID, Optional.empty(), 60);

        ArgumentCaptor<LocalDate> cursor = ArgumentCaptor.forClass(LocalDate.class);
        then(entries).should().findPageBefore(eq(USER_ID), cursor.capture(), any());
        assertThat(cursor.getValue()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    @DisplayName("should reject a limit outside the allowed range")
    void shouldRejectLimitOutsideRange() {
        givenUserExists();

        assertThatThrownBy(() -> service.history(USER_ID, Optional.empty(), 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.history(USER_ID, Optional.empty(), 201))
                .isInstanceOf(BadRequestException.class);

        then(entries).should(never()).findPageBefore(any(), any(), any());
    }

    // --- T-34 promedio interno ---------------------------------------------------------------

    @Test
    @DisplayName("should average valence and arousal separately")
    void shouldAverageValenceAndArousalSeparately() {
        givenCatalog();
        given(
                        entries.findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                                USER_ID, TODAY.minusDays(6), TODAY))
                .willReturn(List.of(entry(TODAY, "HAPPY"), entry(TODAY.minusDays(1), "CALM")));

        MoodAverage average = service.averageLast7Days(USER_ID).orElseThrow();

        // HAPPY (1.00, 0.00) y CALM (0.71, -0.71): medias por componente.
        assertThat(average.valence()).isEqualTo((1.00 + 0.71) / 2);
        assertThat(average.arousal()).isEqualTo((0.00 + -0.71) / 2);
        assertThat(average.days()).isEqualTo(2);
    }

    @Test
    @DisplayName("should return empty when there are no entries in the last 7 days")
    void shouldReturnEmptyWhenNoEntriesInLast7Days() {
        givenCatalog();
        givenEmptyWindow();

        assertThat(service.averageLast7Days(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("should query the inclusive window from today minus six")
    void shouldQueryInclusiveWindowFromTodayMinusSix() {
        givenCatalog();
        givenEmptyWindow();

        service.averageLast7Days(USER_ID);

        then(entries)
                .should()
                .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                        USER_ID, LocalDate.of(2026, 10, 2), TODAY);
    }

    @Test
    @DisplayName("should skip unknown codes in the average")
    void shouldSkipUnknownCodesInAverage() {
        givenCatalog();
        given(
                        entries.findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                                USER_ID, TODAY.minusDays(6), TODAY))
                .willReturn(List.of(entry(TODAY, "HAPPY"), entry(TODAY.minusDays(1), "RETIRED")));

        MoodAverage average = service.averageLast7Days(USER_ID).orElseThrow();

        assertThat(average.days()).isEqualTo(1);
        assertThat(average.valence()).isEqualTo(1.00);
        assertThat(average.arousal()).isEqualTo(0.00);
    }

    // --- helpers -----------------------------------------------------------------------------

    private Clock fixedAt(Instant instant) {
        return Clock.fixed(instant, com.nexus.config.TimeConfig.APP_ZONE);
    }

    private void givenUserExists() {
        given(users.findActiveById(USER_ID)).willReturn(Optional.of(user()));
    }

    private void givenCatalog() {
        given(options.findAllByOrderBySortOrderAsc()).willReturn(catalog());
    }

    private void givenEmptyWindow() {
        given(
                        entries.findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                                eq(USER_ID), any(), any()))
                .willReturn(List.of());
    }

    private static User user() {
        return User.builder().id(USER_ID).build();
    }

    private MoodEntry entry(LocalDate date, String code) {
        return MoodEntry.builder().user(user()).entryDate(date).moodCode(code).build();
    }

    private static List<MoodOption> catalog() {
        return List.of(
                option(1, "EXCITED", "Emocionado", "🤩", "0.71", "0.71", 1),
                option(2, "HAPPY", "Feliz", "😊", "1.00", "0.00", 2),
                option(3, "CALM", "Tranquilo", "😌", "0.71", "-0.71", 3),
                option(4, "TIRED", "Cansado", "😴", "0.00", "-1.00", 4),
                option(5, "SAD", "Triste", "😢", "-0.71", "-0.71", 5),
                option(6, "DOWN", "Desanimado", "😞", "-1.00", "0.00", 6),
                option(7, "STRESSED", "Estresado", "😫", "-0.71", "0.71", 7),
                option(8, "SURPRISED", "Sorprendido", "😮", "0.00", "1.00", 8));
    }

    private static MoodOption option(
            int id, String code, String label, String emoji, String valence, String arousal,
            int sortOrder) {
        try {
            Constructor<MoodOption> ctor = MoodOption.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            MoodOption o = ctor.newInstance();
            setField(o, "id", id);
            setField(o, "code", code);
            setField(o, "label", label);
            setField(o, "emoji", emoji);
            setField(o, "valence", new BigDecimal(valence));
            setField(o, "arousal", new BigDecimal(arousal));
            setField(o, "sortOrder", sortOrder);
            return o;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
