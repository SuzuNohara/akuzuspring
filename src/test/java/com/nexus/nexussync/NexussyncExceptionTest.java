package com.nexus.nexussync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.catalog.CatalogException;
import com.nexus.nexussync.context.ContextException;
import com.nexus.nexussync.decision.DecisionException;
import com.nexus.nexussync.params.ParamsException;
import com.nexus.nexussync.places.PlaceException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Tests de la jerarquía de excepciones de nexussync (§3.0). */
class NexussyncExceptionTest {

  private static final String MESSAGE = "boom";

  /** Una hija de la jerarquía y sus dos constructores públicos. */
  private record Child(
      Class<? extends NexussyncException> type,
      Function<String, NexussyncException> plain,
      BiFunction<String, Throwable, NexussyncException> withCause) {}

  private static List<Child> children() {
    return List.of(
        new Child(ParamsException.class, ParamsException::new, ParamsException::new),
        new Child(CatalogException.class, CatalogException::new, CatalogException::new),
        new Child(ContextException.class, ContextException::new, ContextException::new),
        new Child(DecisionException.class, DecisionException::new, DecisionException::new),
        new Child(PlaceException.class, PlaceException::new, PlaceException::new),
        new Child(
            AnnException.class,
            m -> new AnnException(AnnException.Kind.PARSE, m),
            (m, c) -> new AnnException(AnnException.Kind.PARSE, m, c)));
  }

  static Stream<Arguments> childArguments() {
    return children().stream().map(c -> Arguments.of(c.type().getSimpleName(), c));
  }

  static Stream<Class<?>> hierarchy() {
    return Stream.concat(Stream.of(NexussyncException.class), children().stream().map(Child::type));
  }

  @Test
  void given_base_when_built_with_message_then_it_is_a_checked_exception() {
    NexussyncException ex = new NexussyncException(MESSAGE);

    assertThat(ex).isInstanceOf(Exception.class).isNotInstanceOf(RuntimeException.class);
    assertThat(ex).hasMessage(MESSAGE).hasNoCause();
  }

  @Test
  void given_base_when_built_with_cause_then_cause_is_kept() {
    Throwable cause = new IllegalStateException("root");

    NexussyncException ex = new NexussyncException(MESSAGE, cause);

    assertThat(ex).hasMessage(MESSAGE).hasCause(cause);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("childArguments")
  void given_child_when_built_with_message_then_it_extends_base(String name, Child child) {
    NexussyncException ex = child.plain().apply(MESSAGE);

    assertThat(ex).isExactlyInstanceOf(child.type());
    assertThat(ex).isInstanceOf(NexussyncException.class).hasMessage(MESSAGE).hasNoCause();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("childArguments")
  void given_child_when_built_with_cause_then_cause_is_kept(String name, Child child) {
    Throwable cause = new IllegalArgumentException("root");

    NexussyncException ex = child.withCause().apply(MESSAGE, cause);

    assertThat(ex).isExactlyInstanceOf(child.type()).hasMessage(MESSAGE).hasCause(cause);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("childArguments")
  void given_child_when_thrown_then_catching_base_catches_it(String name, Child child) {
    assertThatThrownBy(
            () -> {
              throw child.plain().apply(MESSAGE);
            })
        .isInstanceOf(NexussyncException.class)
        .hasMessage(MESSAGE);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("hierarchy")
  void given_any_exception_when_inspected_then_serial_version_uid_is_declared(Class<?> type)
      throws NoSuchFieldException {
    Field field = type.getDeclaredField("serialVersionUID");

    assertThat(field.getType()).isEqualTo(long.class);
    assertThat(Modifier.isStatic(field.getModifiers())).isTrue();
    assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
  }

  @ParameterizedTest
  @EnumSource(AnnException.Kind.class)
  void given_ann_kind_when_built_then_kind_is_preserved(AnnException.Kind kind) {
    Throwable cause = new IllegalStateException("root");

    AnnException plain = new AnnException(kind, MESSAGE);
    AnnException withCause = new AnnException(kind, MESSAGE, cause);

    assertThat(plain.kind()).isEqualTo(kind);
    assertThat(withCause.kind()).isEqualTo(kind);
    assertThat(withCause).hasCause(cause);
  }

  @Test
  void given_ann_kind_enum_when_listed_then_it_has_the_seven_kinds_of_the_plan() {
    assertThat(AnnException.Kind.values())
        .containsExactly(
            AnnException.Kind.NOT_FOUND,
            AnnException.Kind.PARSE,
            AnnException.Kind.EXIT,
            AnnException.Kind.TIMEOUT,
            AnnException.Kind.LOCKED,
            AnnException.Kind.RENDER,
            AnnException.Kind.AGENTS);
  }

  @Test
  void given_ann_exception_when_caught_as_base_then_kind_survives_the_downcast() {
    NexussyncException ex = new AnnException(AnnException.Kind.TIMEOUT, MESSAGE);

    assertThat(ex).isInstanceOf(AnnException.class);
    assertThat(((AnnException) ex).kind()).isEqualTo(AnnException.Kind.TIMEOUT);
  }
}
