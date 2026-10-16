package com.nexus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [T-06] P5 - La aplicacion no depende de nexussync: ninguna clase fuera de su paquete lo importa.
 * El paquete se queda en el repositorio hasta la limpieza del cierre del proyecto.
 */
class NoNexussyncImportsTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "nexus");
    private static final Path NEXUSSYNC = MAIN.resolve("nexussync");

    @Test
    @DisplayName("P5: should not import com.nexus.nexussync outside its own package")
    void shouldNotImportNexussyncOutsideItsOwnPackage() throws IOException {
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(MAIN)) {
            offenders =
                    files.filter(p -> p.toString().endsWith(".java"))
                            .filter(p -> !p.startsWith(NEXUSSYNC))
                            .filter(NoNexussyncImportsTest::importsNexussync)
                            .toList();
        }

        assertThat(offenders).isEmpty();
    }

    private static boolean importsNexussync(Path file) {
        try {
            return Files.readString(file).contains("import com.nexus.nexussync");
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + file, e);
        }
    }
}
