package com.nexus.nexussync.bench;

import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.rounds.Agent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Markdown report comparing experiments (unit U11, §3.11).
 *
 * <p>Each experiment directory ({@code runs/<experiment>}) is scanned for the {@code record.json}
 * written by {@link CoupleRunner} ({@code <coupleId>/<runId>/record.json}); its runs feed {@link
 * Metrics#of}. The report {@code compare-<yyyy-MM-dd>.md} has one row per metric plus the number of
 * runs, one column per experiment (named after its directory), {@value #NOT_AVAILABLE} for NaN and
 * a fixed note about the bench bias X5.
 */
public final class Comparer {

  /** Text written for a metric without data. */
  static final String NOT_AVAILABLE = "n/a";

  /** Fixed note about the X5 bias of the bench. */
  static final String X5_NOTE =
      "> **Sesgo X5 del banco.** `truthAlignment` y `chosenInTop3TruthRate` son optimistas por"
          + " construcción: `TruthRanker` puntúa con el mismo modelo de rasgos que `Sampler`. El"
          + " ruido `truth_noise` (A1) atenúa el sesgo pero no lo elimina; no leer estas métricas"
          + " como satisfacción real de la pareja.";

  private static final int RECORD_DEPTH = 3;

  private Comparer() {}

  /**
   * Writes the comparison dated today (system default zone).
   *
   * @param expDirs experiment directories, one column each, in this order
   * @param out directory of the reports, created if missing
   * @return the written report
   * @throws UncheckedIOException if a record cannot be read or the report cannot be written
   * @implNote O(r · cost of {@link Metrics#of} per run + e · m) time, r runs, e experiments, m
   *     metrics; O(r) space.
   */
  public static Path compare(List<Path> expDirs, Path out) {
    return compare(expDirs, out, LocalDate.now(Clock.systemDefaultZone()));
  }

  /**
   * Writes {@code out/compare-<date>.md}.
   *
   * @param expDirs experiment directories, one column each, in this order
   * @param out directory of the reports, created if missing
   * @param date date in the name and title of the report
   * @return the written report
   * @throws UncheckedIOException if a record cannot be read or the report cannot be written
   * @implNote Same as {@link #compare(List, Path)}.
   */
  public static Path compare(List<Path> expDirs, Path out, LocalDate date) {
    return compare(expDirs, out, date, Map.of());
  }

  /**
   * Writes {@code out/compare-<date>.md}, restoring the hidden truth of the runs from the couple
   * files of {@code couplesDir} (D-33: {@code record.json} only holds its hash).
   *
   * @param expDirs experiment directories, one column each, in this order
   * @param out directory of the reports, created if missing
   * @param date date in the name and title of the report
   * @param couplesDir directory of the couple fixtures; missing means no truth
   * @return the written report
   * @throws UncheckedIOException if a record or couple cannot be read or the report cannot be
   *     written
   * @implNote Same as {@link #compare(List, Path)} plus O(c) couple files read.
   */
  public static Path compare(List<Path> expDirs, Path out, LocalDate date, Path couplesDir) {
    return compare(expDirs, out, date, TruthRedaction.fixtureTruth(couplesDir));
  }

  private static Path compare(
      List<Path> expDirs,
      Path out,
      LocalDate date,
      Map<String, Map<Agent, Map<Feature, Double>>> truth) {
    List<String> names = new ArrayList<>();
    List<Map<String, Double>> metrics = new ArrayList<>();
    List<Integer> counts = new ArrayList<>();
    for (Path dir : expDirs) {
      List<RunRecord> runs = records(dir, truth);
      names.add(String.valueOf(dir.getFileName()));
      metrics.add(Metrics.of(runs));
      counts.add(runs.size());
    }
    Path report = out.resolve("compare-" + date + ".md");
    try {
      Files.createDirectories(out);
      Files.writeString(report, render(date, names, counts, metrics), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot write " + report, e);
    }
    return report;
  }

  /**
   * Reads every {@code record.json} under an experiment directory, in path order.
   *
   * @param expDir experiment directory; a missing directory has no runs
   * @return the records
   * @throws UncheckedIOException if the directory cannot be walked or a record cannot be parsed
   * @implNote O(f + r) time, f files walked, r records; O(r) space.
   */
  static List<RunRecord> records(Path expDir) {
    return records(expDir, Map.of());
  }

  /**
   * Reads every {@code record.json} under an experiment directory, in path order, restoring the
   * truth whose hash matches ({@link TruthRedaction#read}).
   *
   * @param expDir experiment directory; a missing directory has no runs
   * @param truth truth weights by couple id and person
   * @return the records
   * @throws UncheckedIOException if the directory cannot be walked or a record cannot be parsed
   * @implNote O(f + r) time, f files walked, r records; O(r) space.
   */
  static List<RunRecord> records(Path expDir, Map<String, Map<Agent, Map<Feature, Double>>> truth) {
    if (!Files.isDirectory(expDir)) {
      return List.of();
    }
    List<Path> files;
    try (Stream<Path> walk = Files.walk(expDir, RECORD_DEPTH)) {
      files =
          walk.filter(f -> CoupleRunner.RECORD.equals(String.valueOf(f.getFileName())))
              .filter(Files::isRegularFile)
              .sorted()
              .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("cannot scan " + expDir, e);
    }
    List<RunRecord> out = new ArrayList<>();
    for (Path f : files) {
      try {
        out.add(TruthRedaction.read(BenchIo.JSON.readTree(f.toFile()), truth));
      } catch (IOException e) {
        throw new UncheckedIOException("cannot read " + f, e);
      }
    }
    return out;
  }

  private static String render(
      LocalDate date, List<String> names, List<Integer> counts, List<Map<String, Double>> ms) {
    StringBuilder md = new StringBuilder();
    md.append("# Comparación de experimentos — ").append(date).append("\n\n");
    md.append("| Métrica |");
    names.forEach(n -> md.append(' ').append(n).append(" |"));
    md.append("\n|---|");
    names.forEach(n -> md.append("---|"));
    md.append("\n| runs |");
    counts.forEach(c -> md.append(' ').append(c).append(" |"));
    md.append('\n');
    for (String metric : Metrics.NAMES) {
      md.append("| ").append(metric).append(" |");
      ms.forEach(m -> md.append(' ').append(format(m.get(metric))).append(" |"));
      md.append('\n');
    }
    md.append('\n').append(X5_NOTE).append('\n');
    return md.toString();
  }

  /**
   * Formats a metric value with four decimals, {@value #NOT_AVAILABLE} for NaN or a missing value.
   *
   * @param value metric value, possibly {@code null} or NaN
   * @return the cell text
   * @implNote O(1) time and space.
   */
  static String format(Double value) {
    if (value == null || value.isNaN()) {
      return NOT_AVAILABLE;
    }
    return String.format(Locale.ROOT, "%.4f", value);
  }
}
