package com.nexus.nexussync.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.params.DecisionParams;
import java.util.List;
import org.junit.jupiter.api.Test;

class MoreOptionsTest {

  private static final List<String> REMAINING = List.of("a1", "a2", "a3", "a4", "a5", "a6", "a7");

  private static DecisionParams params(int batch) {
    return new DecisionParams(List.of(3, 2, 1), batch, 48, 0.5, 0.5, 30);
  }

  // U8-06
  @Test
  void givenSevenRemaining_whenPagingByFive_thenFiveTwoAndEmpty() {
    DecisionParams p = params(5);

    assertThat(MoreOptions.next(REMAINING, 0, p)).containsExactly("a1", "a2", "a3", "a4", "a5");
    assertThat(MoreOptions.next(REMAINING, 5, p)).containsExactly("a6", "a7");
    assertThat(MoreOptions.next(REMAINING, 7, p)).isEmpty();
    assertThat(MoreOptions.next(REMAINING, 99, p)).isEmpty();
  }

  @Test
  void givenNonPositiveBatch_whenNext_thenEmpty() {
    assertThat(MoreOptions.next(REMAINING, 0, params(0))).isEmpty();
  }

  @Test
  void givenNegativeOffset_whenNext_thenIllegalArgument() {
    assertThatThrownBy(() -> MoreOptions.next(REMAINING, -1, params(5)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void givenPage_whenModified_thenUnsupported() {
    List<String> page = MoreOptions.next(REMAINING, 0, params(5));

    assertThatThrownBy(() -> page.add("x")).isInstanceOf(UnsupportedOperationException.class);
  }
}
