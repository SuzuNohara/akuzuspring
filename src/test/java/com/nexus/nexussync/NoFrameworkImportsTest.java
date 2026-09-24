package com.nexus.nexussync;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * U12-09: the whole {@code com.nexus.nexussync} package (main and test) is plain Java; no Spring,
 * Jakarta, Lombok nor any {@code com.nexus} class outside the package.
 */
class NoFrameworkImportsTest {

  private static final Pattern FORBIDDEN =
      Pattern.compile(
          "^import\\s+(static\\s+)?"
              + "(org\\.springframework\\.|jakarta\\.|lombok\\.|com\\.nexus\\.(?!nexussync[.;]))");

  private static final List<Path> ROOTS =
      List.of(
          Path.of("src", "main", "java", "com", "nexus", "nexussync"),
          Path.of("src", "test", "java", "com", "nexus", "nexussync"));

  // U12-09
  @Test
  void given_package_sources_when_scan_imports_then_no_framework_nor_foreign_nexus()
      throws IOException {
    List<String> offending = new ArrayList<>();
    int scanned = 0;
    for (Path root : ROOTS) {
      assertThat(root).as("run from the backend base directory").isDirectory();
      for (Path file : javaFiles(root)) {
        scanned++;
        offending.addAll(violations(file));
      }
    }

    assertThat(scanned).isGreaterThan(100);
    assertThat(offending).isEmpty();
  }

  @Test
  void given_forbidden_and_allowed_imports_when_match_then_only_forbidden_flagged() {
    List<String> forbidden =
        List.of(
            "import org.springframework.stereotype.Service;",
            "import jakarta.persistence.Entity;",
            "import lombok.Data;",
            "import com.nexus.entity.User;",
            "import static com.nexus.util.LinkCodeGenerator.generate;",
            "import com.nexus.nexussyncx.Other;");
    List<String> allowed =
        List.of(
            "import com.nexus.nexussync.params.Params;",
            "import static com.nexus.nexussync.cli.CliFixture.BASELINE;",
            "import java.util.List;",
            "// import org.springframework.Foo;");

    assertThat(forbidden).allMatch(line -> FORBIDDEN.matcher(line).find());
    assertThat(allowed).noneMatch(line -> FORBIDDEN.matcher(line).find());
  }

  private static List<Path> javaFiles(Path root) throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(f -> f.toString().endsWith(".java")).sorted().toList();
    }
  }

  private static List<String> violations(Path file) throws IOException {
    List<String> out = new ArrayList<>();
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      if (FORBIDDEN.matcher(line.strip()).find()) {
        out.add(file + ": " + line.strip());
      }
    }
    return out;
  }
}
