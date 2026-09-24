package com.nexus.nexussync.ann;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CallBudgetTest {

  // U6-11
  @Test
  void givenBudgetOfTwo_whenConsumingThreeTimes_thenThirdIsRefused() {
    CallBudget budget = new CallBudget(2);

    assertThat(budget.tryConsume()).isTrue();
    assertThat(budget.tryConsume()).isTrue();
    assertThat(budget.tryConsume()).isFalse();
    assertThat(budget.used()).isEqualTo(2);
  }

  // U6-11
  @Test
  void givenZeroBudget_whenConsuming_thenRefusedAndNothingUsed() {
    CallBudget budget = new CallBudget(0);

    assertThat(budget.tryConsume()).isFalse();
    assertThat(budget.used()).isZero();
  }

  @Test
  void givenNegativeMax_whenCreating_thenIllegalArgument() {
    assertThatThrownBy(() -> new CallBudget(-1)).isInstanceOf(IllegalArgumentException.class);
  }
}
