package com.nexus.nexussync.cli;

import com.nexus.nexussync.params.ExecutorKind;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the command line of the nexussync CLI into one of the argument records (unit U11).
 *
 * <p>Syntax: {@code <command> --key value …} or {@code --key=value}. Each command accepts a fixed
 * set of options; an unknown command, an unknown or repeated option, a missing value, a missing
 * required option or a malformed number is a usage error ({@code Optional.empty()}, exit code 2).
 * {@code --couples}, {@code --exps} and {@code --seeds} are comma separated lists; blank items are
 * dropped. {@code sweep} defaults: {@code --couples all}, {@code --seeds 1,2,3}, {@code --executor
 * ORACLE}, {@code --rounds 1}; {@code --executor REPLAY} is a usage error.
 */
public final class ArgParser {

  /** Usage text logged on a usage error. */
  public static final String USAGE =
      "usage: validate --params <yml> [--dir <nexussync>]"
          + " | run --params <yml> [--dir <nexussync>] --couples <slug,…|all> [--rounds <n>]"
          + " [--seed <n>]"
          + " | replay --params <yml> [--dir <nexussync>] --from <runDir>"
          + " | compare --dir <nexussync> --exps <exp,…>"
          + " | sweep --dir <nexussync> --matrix <yml> [--couples <slug,…|all>]"
          + " [--seeds <n,…>] [--executor ORACLE|ARKANNIE] [--rounds <n>]";

  static final String PARAMS = "params";
  static final String DIR = "dir";
  static final String COUPLES = "couples";
  static final String ROUNDS = "rounds";
  static final String SEED = "seed";
  static final String FROM = "from";
  static final String EXPS = "exps";
  static final String MATRIX = "matrix";
  static final String SEEDS = "seeds";
  static final String EXECUTOR = "executor";

  /** Default seeds of {@code sweep}. */
  static final List<Long> DEFAULT_SEEDS = List.of(1L, 2L, 3L);

  /** Default executor of {@code sweep}: no AI. */
  static final ExecutorKind DEFAULT_EXECUTOR = ExecutorKind.ORACLE;

  private static final Logger LOG = LoggerFactory.getLogger(ArgParser.class);
  private static final String PREFIX = "--";
  private static final int DEFAULT_ROUNDS = 1;

  private ArgParser() {}

  /**
   * Parses {@code args}.
   *
   * @param args command followed by its options
   * @return a {@link ValidateArgs}, {@link RunArgs}, {@link ReplayArgs}, {@link CompareArgs} or
   *     {@link SweepArgs}; empty on a usage error (logged)
   * @implNote O(n) time and space, n = number of arguments.
   */
  public static Optional<Object> parse(String[] args) {
    if (args.length == 0) {
      LOG.error("missing command; {}", USAGE);
      return Optional.empty();
    }
    String command = args[0];
    Optional<Object> out =
        options(Arrays.asList(args).subList(1, args.length))
            .flatMap(opts -> command(command, opts));
    if (out.isEmpty()) {
      LOG.error("invalid arguments; {}", USAGE);
    }
    return out;
  }

  private static Optional<Object> command(String command, Map<String, String> o) {
    return switch (command) {
      case "validate" ->
          accepts(o, Set.of(PARAMS, DIR), Set.of(PARAMS))
              ? Optional.of(new ValidateArgs(Path.of(o.get(PARAMS)), dir(o)))
              : Optional.empty();
      case "run" -> run(o);
      case "replay" ->
          accepts(o, Set.of(PARAMS, DIR, FROM), Set.of(PARAMS, FROM))
              ? Optional.of(new ReplayArgs(Path.of(o.get(PARAMS)), dir(o), Path.of(o.get(FROM))))
              : Optional.empty();
      case "compare" ->
          accepts(o, Set.of(DIR, EXPS), Set.of(DIR, EXPS))
              ? Optional.of(new CompareArgs(Path.of(o.get(DIR)), list(o.get(EXPS))))
              : Optional.empty();
      case "sweep" -> sweep(o);
      default -> Optional.empty();
    };
  }

  private static Optional<Object> run(Map<String, String> o) {
    if (!accepts(o, Set.of(PARAMS, DIR, COUPLES, ROUNDS, SEED), Set.of(PARAMS))) {
      return Optional.empty();
    }
    try {
      int rounds = rounds(o);
      OptionalLong seed =
          o.containsKey(SEED) ? OptionalLong.of(Long.parseLong(o.get(SEED))) : OptionalLong.empty();
      if (rounds < 1) {
        return Optional.empty();
      }
      List<String> couples = o.containsKey(COUPLES) ? list(o.get(COUPLES)) : List.of();
      return Optional.of(new RunArgs(Path.of(o.get(PARAMS)), dir(o), couples, rounds, seed));
    } catch (NumberFormatException e) {
      LOG.error("not a number: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private static Optional<Object> sweep(Map<String, String> o) {
    if (!accepts(o, Set.of(DIR, MATRIX, COUPLES, SEEDS, EXECUTOR, ROUNDS), Set.of(DIR, MATRIX))) {
      return Optional.empty();
    }
    try {
      List<Long> seeds =
          o.containsKey(SEEDS)
              ? list(o.get(SEEDS)).stream().map(Long::parseLong).toList()
              : DEFAULT_SEEDS;
      ExecutorKind executor =
          o.containsKey(EXECUTOR)
              ? ExecutorKind.valueOf(o.get(EXECUTOR).strip().toUpperCase(Locale.ROOT))
              : DEFAULT_EXECUTOR;
      List<String> couples = o.containsKey(COUPLES) ? list(o.get(COUPLES)) : List.of(Commands.ALL);
      return Optional.of(
          new SweepArgs(
              Path.of(o.get(DIR)), Path.of(o.get(MATRIX)), couples, seeds, executor, rounds(o)));
    } catch (IllegalArgumentException e) {
      LOG.error("invalid sweep option: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /** {@code --rounds}, {@value #DEFAULT_ROUNDS} when absent. */
  private static int rounds(Map<String, String> o) {
    return o.containsKey(ROUNDS) ? Integer.parseInt(o.get(ROUNDS)) : DEFAULT_ROUNDS;
  }

  /** Splits {@code --key value} / {@code --key=value} pairs; empty on any malformed token. */
  static Optional<Map<String, String>> options(List<String> tokens) {
    Map<String, String> out = new HashMap<>();
    int i = 0;
    while (i < tokens.size()) {
      String token = tokens.get(i);
      if (!token.startsWith(PREFIX) || token.length() == PREFIX.length()) {
        return Optional.empty();
      }
      String body = token.substring(PREFIX.length());
      int eq = body.indexOf('=');
      String key = eq < 0 ? body : body.substring(0, eq);
      String value;
      if (eq >= 0) {
        value = body.substring(eq + 1);
        i++;
      } else if (i + 1 < tokens.size()) {
        value = tokens.get(i + 1);
        i += 2;
      } else {
        return Optional.empty();
      }
      if (out.put(key, value) != null) {
        return Optional.empty();
      }
    }
    return Optional.of(out);
  }

  private static boolean accepts(Map<String, String> o, Set<String> allowed, Set<String> required) {
    return allowed.containsAll(o.keySet()) && o.keySet().containsAll(required);
  }

  private static Optional<Path> dir(Map<String, String> o) {
    return Optional.ofNullable(o.get(DIR)).map(Path::of);
  }

  private static List<String> list(String value) {
    return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
  }
}
