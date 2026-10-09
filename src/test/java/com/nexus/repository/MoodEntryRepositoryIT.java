package com.nexus.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.config.JpaAuditingConfig;
import com.nexus.entity.MoodEntry;
import com.nexus.entity.User;
import com.nexus.harness.MySqlSchemaSupport;
import com.nexus.harness.TestUsers;
import com.nexus.security.FieldCipher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Contrato entre {@link MoodEntry}, el {@code MoodCodeConverter} y la tabla {@code mood_entries}
 * sobre MySQL 8 real (nexus-EMO-01, R1/R5/R10/R15). Importa {@link FieldCipher} para que Hibernate
 * pueda inyectarlo en el converter (RK-2).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, FieldCipher.class})
class MoodEntryRepositoryIT extends MySqlSchemaSupport {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 8);

    @Autowired MoodEntryRepository repository;
    @Autowired TestEntityManager em;

    private MoodEntry entry(User user, LocalDate date, String code) {
        return MoodEntry.builder().user(user).entryDate(date).moodCode(code).build();
    }

    @Test
    @DisplayName("should round trip mood code when persisted and reloaded")
    void shouldRoundTripMoodCodeWhenPersistedAndReloaded() {
        User user = TestUsers.persist(em, "roundtrip@mood.test");
        MoodEntry saved = em.persistAndFlush(entry(user, DAY, "CALM"));
        em.clear();

        MoodEntry reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getMoodCode()).isEqualTo("CALM");
    }

    @Test
    @DisplayName("should store only fixed length ciphertext when read natively")
    void shouldStoreOnlyFixedLengthCiphertextWhenReadNatively() {
        User user = TestUsers.persist(em, "cipher@mood.test");
        MoodEntry saved = em.persistAndFlush(entry(user, DAY, "CALM"));
        em.clear();

        Object[] row =
                (Object[])
                        em.getEntityManager()
                                .createNativeQuery(
                                        "SELECT LENGTH(mood_cipher), mood_cipher FROM mood_entries WHERE id = ?1")
                                .setParameter(1, saved.getId())
                                .getSingleResult();

        assertThat(((Number) row[0]).intValue()).isEqualTo(61);
        assertThat(new String((byte[]) row[1], StandardCharsets.US_ASCII)).doesNotContain("CALM");
    }

    @Test
    @DisplayName("should reject second entry when user and day repeat")
    void shouldRejectSecondEntryWhenUserAndDayRepeat() {
        User user = TestUsers.persist(em, "dup@mood.test");
        em.persistAndFlush(entry(user, DAY, "CALM"));

        // saveAndFlush (no em.persistAndFlush) para que la traducción de excepciones de Spring Data
        // convierta la violación de UNIQUE en DataIntegrityViolationException.
        assertThatThrownBy(() -> repository.saveAndFlush(entry(user, DAY, "HAPPY")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should allow same day for different users")
    void shouldAllowSameDayForDifferentUsers() {
        User a = TestUsers.persist(em, "a@mood.test");
        User b = TestUsers.persist(em, "b@mood.test");

        em.persistAndFlush(entry(a, DAY, "CALM"));
        em.persistAndFlush(entry(b, DAY, "HAPPY"));

        assertThat(repository.findByUserIdAndEntryDate(b.getId(), DAY)).isPresent();
    }

    @Test
    @DisplayName("should cascade delete entries when user row is deleted natively")
    void shouldCascadeDeleteEntriesWhenUserRowIsDeletedNatively() {
        User user = TestUsers.persist(em, "cascade@mood.test");
        em.persistAndFlush(entry(user, DAY, "CALM"));
        Long userId = user.getId();
        em.clear();

        em.getEntityManager()
                .createNativeQuery("DELETE FROM users WHERE id = ?1")
                .setParameter(1, userId)
                .executeUpdate();
        Number remaining =
                (Number)
                        em.getEntityManager()
                                .createNativeQuery(
                                        "SELECT COUNT(*) FROM mood_entries WHERE user_id = ?1")
                                .setParameter(1, userId)
                                .getSingleResult();

        assertThat(remaining.intValue()).isZero();
    }

    @Test
    @DisplayName("should store entry date without shift when read natively")
    void shouldStoreEntryDateWithoutShiftWhenReadNatively() {
        User user = TestUsers.persist(em, "date@mood.test");
        MoodEntry saved = em.persistAndFlush(entry(user, DAY, "CALM"));
        em.clear();

        String stored =
                (String)
                        em.getEntityManager()
                                .createNativeQuery(
                                        "SELECT DATE_FORMAT(entry_date, '%Y-%m-%d') FROM mood_entries WHERE id = ?1")
                                .setParameter(1, saved.getId())
                                .getSingleResult();

        assertThat(stored).isEqualTo("2026-10-08");
    }

    @Test
    @DisplayName("should not touch updated at when flushed without changes")
    void shouldNotTouchUpdatedAtWhenFlushedWithoutChanges() {
        User user = TestUsers.persist(em, "dirty@mood.test");
        Long id = em.persistAndFlush(entry(user, DAY, "CALM")).getId();
        em.clear();

        // before/after se leen AMBOS de la base (DATETIME(3) → millis): comparar con el Instant en
        // memoria del persist fallaría por precisión, no por un UPDATE espurio.
        MoodEntry reloaded = repository.findById(id).orElseThrow();
        Instant before = reloaded.getUpdatedAt();
        em.flush();
        em.clear();

        MoodEntry again = repository.findById(id).orElseThrow();
        assertThat(again.getMoodCode()).isEqualTo("CALM");
        assertThat(again.getUpdatedAt()).isEqualTo(before);
    }

    @Test
    @DisplayName("should return entries before cursor in descending order up to limit")
    void shouldReturnEntriesBeforeCursorInDescendingOrderUpToLimit() {
        User user = TestUsers.persist(em, "page@mood.test");
        em.persistAndFlush(entry(user, DAY.minusDays(2), "SAD"));
        em.persistAndFlush(entry(user, DAY.minusDays(1), "TIRED"));
        em.persistAndFlush(entry(user, DAY, "CALM"));
        em.clear();

        List<MoodEntry> page = repository.findPageBefore(user.getId(), DAY, PageRequest.of(0, 2));

        assertThat(page).extracting(MoodEntry::getEntryDate)
                .containsExactly(DAY.minusDays(1), DAY.minusDays(2));
    }

    @Test
    @DisplayName("should return range in descending order when between")
    void shouldReturnRangeInDescendingOrderWhenBetween() {
        User user = TestUsers.persist(em, "range@mood.test");
        em.persistAndFlush(entry(user, DAY.minusDays(2), "SAD"));
        em.persistAndFlush(entry(user, DAY.minusDays(1), "TIRED"));
        em.persistAndFlush(entry(user, DAY, "CALM"));
        em.clear();

        List<MoodEntry> range =
                repository.findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                        user.getId(), DAY.minusDays(2), DAY);

        assertThat(range).extracting(MoodEntry::getEntryDate)
                .containsExactly(DAY, DAY.minusDays(1), DAY.minusDays(2));
    }
}
