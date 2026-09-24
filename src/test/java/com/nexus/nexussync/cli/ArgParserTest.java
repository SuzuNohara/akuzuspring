package com.nexus.nexussync.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/** Tests of the command line parser. */
class ArgParserTest {

  @Test
  void given_validate_when_parse_then_validate_args() {
    Optional<Object> out = ArgParser.parse(new String[] {"validate", "--params", "p.yml"});

    assertThat(out).contains(new ValidateArgs(Path.of("p.yml"), Optional.empty()));
  }

  @Test
  void given_run_with_every_option_when_parse_then_run_args() {
    Optional<Object> out =
        ArgParser.parse(
            new String[] {
              "run",
              "--params",
              "p.yml",
              "--dir=/nx",
              "--couples",
              " a, b ,,",
              "--rounds",
              "3",
              "--seed",
              "-7"
            });

    assertThat(out)
        .contains(
            new RunArgs(
                Path.of("p.yml"),
                Optional.of(Path.of("/nx")),
                List.of("a", "b"),
                3,
                OptionalLong.of(-7L)));
  }

  @Test
  void given_run_without_couples_when_parse_then_empty_list_and_one_round() {
    Optional<Object> out = ArgParser.parse(new String[] {"run", "--params", "p.yml"});

    assertThat(out)
        .contains(
            new RunArgs(Path.of("p.yml"), Optional.empty(), List.of(), 1, OptionalLong.empty()));
  }

  @Test
  void given_blank_couples_when_parse_then_empty_list() {
    Optional<Object> out =
        ArgParser.parse(new String[] {"run", "--params", "p.yml", "--couples", ""});

    assertThat(out).get().isInstanceOf(RunArgs.class);
    assertThat(((RunArgs) out.get()).couples()).isEmpty();
  }

  @Test
  void given_replay_when_parse_then_replay_args() {
    Optional<Object> out =
        ArgParser.parse(
            new String[] {"replay", "--params", "p.yml", "--dir", "/nx", "--from", "runs/x"});

    assertThat(out)
        .contains(new ReplayArgs(Path.of("p.yml"), Optional.of(Path.of("/nx")), Path.of("runs/x")));
  }

  @Test
  void given_compare_when_parse_then_compare_args() {
    Optional<Object> out =
        ArgParser.parse(new String[] {"compare", "--dir", "/nx", "--exps", "a,b"});

    assertThat(out).contains(new CompareArgs(Path.of("/nx"), List.of("a", "b")));
  }

  // U11-08 (uso → 2)
  @Test
  void given_malformed_command_lines_when_parse_then_empty() {
    List<String[]> bad =
        List.of(
            new String[] {},
            new String[] {"bogus"},
            new String[] {"validate"},
            new String[] {"validate", "--params"},
            new String[] {"validate", "params", "p.yml"},
            new String[] {"validate", "--", "p.yml"},
            new String[] {"validate", "--params", "p.yml", "--params", "q.yml"},
            new String[] {"validate", "--params", "p.yml", "--from", "x"},
            new String[] {"run", "--params", "p.yml", "--rounds", "x"},
            new String[] {"run", "--params", "p.yml", "--rounds", "0"},
            new String[] {"run", "--params", "p.yml", "--seed", "1.5"},
            new String[] {"run", "--couples", "a"},
            new String[] {"replay", "--params", "p.yml"},
            new String[] {"compare", "--dir", "/nx"});

    for (String[] args : bad) {
      assertThat(ArgParser.parse(args)).as(String.join(" ", args)).isEmpty();
    }
  }

  @Test
  void given_equals_syntax_when_options_then_split_at_first_equals() {
    assertThat(ArgParser.options(List.of("--exps=a=b", "--dir", "x")))
        .contains(Map.of("exps", "a=b", "dir", "x"));
  }
}
