package com.nexus.nexussync;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles every source of {@code com.nexus.nexussync} in-process with {@code -Xlint:all -Werror}.
 *
 * <p>Replaces the {@code nexussync-lint} compiler execution foreseen in the plan (the {@code
 * outputDirectory} of maven-compiler-plugin is read-only), so the package stays warning-free
 * without turning {@code -Werror} on for the legacy backend code.
 */
class LintTest {

  private static final Path SOURCE_ROOT =
      Path.of(System.getProperty("basedir", ""))
          .resolve("src")
          .resolve("main")
          .resolve("java")
          .resolve("com")
          .resolve("nexus")
          .resolve("nexussync");

  private static final Set<Diagnostic.Kind> OFFENDING =
      EnumSet.of(Diagnostic.Kind.ERROR, Diagnostic.Kind.WARNING, Diagnostic.Kind.MANDATORY_WARNING);

  // U12-01
  @Test
  void given_package_sources_when_compiled_with_xlint_all_werror_then_no_diagnostics(
      @TempDir Path out) throws IOException {
    List<Path> sources = sources();
    if (sources.isEmpty()) {
      return;
    }
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler).as("system Java compiler").isNotNull();
    DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();

    boolean ok;
    try (StandardJavaFileManager files =
        compiler.getStandardFileManager(collector, Locale.ROOT, StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(sources);
      ok = compiler.getTask(null, files, collector, options(out), null, units).call();
    }
    List<String> offending =
        collector.getDiagnostics().stream()
            .filter(d -> OFFENDING.contains(d.getKind()))
            .map(LintTest::format)
            .toList();

    assertThat(offending).as("javac diagnostics for %d sources", sources.size()).isEmpty();
    assertThat(ok).as("javac result").isTrue();
  }

  private static List<Path> sources() throws IOException {
    if (!Files.isDirectory(SOURCE_ROOT)) {
      return List.of();
    }
    try (Stream<Path> walk = Files.walk(SOURCE_ROOT)) {
      return walk.filter(p -> p.toString().endsWith(".java")).filter(Files::isRegularFile).toList();
    }
  }

  private static List<String> options(Path out) {
    String classpath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    return List.of(
        "-Xlint:all,-processing",
        "-Werror",
        "-proc:none",
        "--release",
        "17",
        "-d",
        out.toString(),
        "-classpath",
        classpath);
  }

  private static String format(Diagnostic<? extends JavaFileObject> d) {
    String source = d.getSource() == null ? "<no source>" : d.getSource().getName();
    return d.getKind() + " " + source + ":" + d.getLineNumber() + " " + d.getMessage(Locale.ROOT);
  }
}
