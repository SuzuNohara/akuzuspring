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

  // D-28
  @Test
  void given_budgetOfFive_when_reservingThreeThenThree_then_secondRefusedAndNothingConsumed() {
    CallBudget budget = new CallBudget(5);

    assertThat(budget.tryConsume(3)).isTrue();
    assertThat(budget.tryConsume(3)).isFalse();
    assertThat(budget.used()).isEqualTo(3);
    assertThat(budget.tryConsume(2)).isTrue();
    assertThat(budget.used()).isEqualTo(5);
    assertThat(budget.tryConsume(0)).isTrue();
    assertThat(budget.tryConsume(1)).isFalse();
  }

  // D-28
  @Test
  void given_negativeReservation_when_tryConsume_then_illegalArgument() {
    CallBudget budget = new CallBudget(5);

    assertThatThrownBy(() -> budget.tryConsume(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThat(budget.used()).isZero();
  }

  @Test
  void givenNegativeMax_whenCreating_thenIllegalArgument() {
    assertThatThrownBy(() -> new CallBudget(-1)).isInstanceOf(IllegalArgumentException.class);
  }
}
