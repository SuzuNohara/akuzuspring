package com.nexus.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.config.JpaAuditingConfig;
import com.nexus.entity.MoodOption;
import com.nexus.harness.MySqlSchemaSupport;
import com.nexus.security.FieldCipher;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Contrato entre {@link MoodOption} y las 8 filas que siembra {@code db/add_mood_tables.sql} sobre
 * MySQL 8 real (nexus-EMO-01, R8/R17).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, FieldCipher.class}) // FieldCipher: el metamodelo incluye MoodEntry y su converter (RK-2)
class MoodOptionRepositoryIT extends MySqlSchemaSupport {

    private static final BigDecimal MINUS_ONE = BigDecimal.valueOf(-1);
    private static final BigDecimal ONE = BigDecimal.ONE;

    @Autowired MoodOptionRepository repository;

    @Test
    @DisplayName("should seed eight options ordered by sort order")
    void shouldSeedEightOptionsOrderedBySortOrder() {
        List<MoodOption> options = repository.findAllByOrderBySortOrderAsc();

        assertThat(options).hasSize(8);
        assertThat(options).extracting(MoodOption::getSortOrder).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(options.get(0).getCode()).isEqualTo("EXCITED");
        assertThat(options).extracting(MoodOption::getCode).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("should keep valence and arousal within unit range")
    void shouldKeepValenceAndArousalWithinUnitRange() {
        for (MoodOption option : repository.findAllByOrderBySortOrderAsc()) {
            assertThat(option.getValence()).isBetween(MINUS_ONE, ONE);
            assertThat(option.getArousal()).isBetween(MINUS_ONE, ONE);
        }
    }

    @Test
    @DisplayName("should read emoji intact when stored as utf8mb4")
    void shouldReadEmojiIntactWhenStoredAsUtf8mb4() {
        MoodOption calm =
                repository.findAllByOrderBySortOrderAsc().stream()
                        .filter(o -> o.getCode().equals("CALM"))
                        .findFirst()
                        .orElseThrow();

        assertThat(calm.getEmoji()).isEqualTo("😌");
    }
}
