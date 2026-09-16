package com.nexus.harness;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * MySQL 8 real con el esquema del proyecto, compartido por todos los {@code *IT} de repositorio.
 *
 * <p>El esquema base ({@link #SCHEMA}) entra por {@code /docker-entrypoint-initdb.d/}: {@code
 * Nexus.sql} usa {@code DELIMITER} y {@code ScriptUtils} no lo soporta, asi que lo aplica el propio
 * cliente {@code mysql} de la imagen. Las migraciones ({@link #MIGRATIONS}) NO pueden ir por ahi:
 * el entrypoint aborta al primer error y {@code add_fcm_token.sql} da 1060 porque {@code Nexus.sql}
 * ya trae la columna. Se aplican despues del arranque con {@code mysql --force}, tolerando
 * exactamente los tres codigos que tolera {@code make db-migrate} (1050, 1060, 1061).
 *
 * <p>{@link #MIGRATIONS} es la unica lista que hay que tocar cuando entra una migracion nueva en
 * {@code DB_MIGRATIONS} del Makefile. Patron "contenedor singleton": se arranca una vez por JVM en
 * el inicializador estatico y no se para (Ryuk lo recoge); {@code @ServiceConnection} sin
 * {@code @Container} para que la extension de JUnit no lo pare entre clases.
 */
public abstract class MySqlSchemaSupport {

  /** Ficheros de DB_SCHEMA del Makefile, en su orden. */
  static final String[] SCHEMA = {"db/Nexus.sql", "db/link_codes.sql"};

  /** Ficheros de DB_MIGRATIONS del Makefile, en su orden. */
  static final String[] MIGRATIONS = {"db/add_fcm_token.sql", "db/add_availability_schedules.sql"};

  /** Los tres codigos que db-migrate lee como "este objeto ya existe". */
  static final Set<String> TOLERATED = Set.of("1050", "1060", "1061");

  static final String DB = "nexus";
  static final String USER = "nexus";
  static final String PASSWORD = "nexus";
  static final String MIGRATIONS_DIR = "/nexus-migrations";

  private static final Pattern ERROR_CODE = Pattern.compile("ERROR (\\d{4}) ");

  @ServiceConnection
  protected static final MySQLContainer<?> MYSQL = start();

  private static MySQLContainer<?> start() {
    MySQLContainer<?> container =
        new MySQLContainer<>("mysql:8.0")
            .withDatabaseName(DB)
            .withUsername(USER)
            .withPassword(PASSWORD)
            .withUrlParam("serverTimezone", "UTC");
    int n = 1;
    for (String file : SCHEMA) {
      container =
          container.withCopyFileToContainer(
              MountableFile.forClasspathResource(file),
              String.format("/docker-entrypoint-initdb.d/%02d-%s", n++, baseName(file)));
    }
    for (int i = 0; i < MIGRATIONS.length; i++) {
      container =
          container.withCopyFileToContainer(
              MountableFile.forClasspathResource(MIGRATIONS[i]), migrationPath(i + 1, MIGRATIONS[i]));
    }
    container.start();
    for (int i = 0; i < MIGRATIONS.length; i++) {
      List<String> codes = applyMigration(container, migrationPath(i + 1, MIGRATIONS[i]));
      if (!TOLERATED.containsAll(codes)) {
        throw new IllegalStateException(
            "La migracion " + MIGRATIONS[i] + " fallo con codigos no tolerados: " + codes);
      }
    }
    return container;
  }

  /**
   * Aplica un fichero SQL ya copiado al contenedor con {@code mysql --force} (sigue tras cada
   * error, como db-migrate sentencia a sentencia) y devuelve los codigos {@code ERROR nnnn} vistos.
   */
  protected static List<String> applyMigration(MySQLContainer<?> container, String containerPath) {
    try {
      ExecResult result =
          container.execInContainer(
              "sh",
              "-c",
              "MYSQL_PWD=" + PASSWORD + " mysql --force -u" + USER + " " + DB + " < " + containerPath
                  + " 2>&1");
      return ERROR_CODE.matcher(result.getStdout()).results().map(m -> m.group(1)).toList();
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException("No se pudo aplicar " + containerPath, e);
    }
  }

  /** Ruta en el contenedor de la migracion i-esima (1-based) de {@link #MIGRATIONS}. */
  protected static String migrationPath(int n, String classpathFile) {
    return String.format("%s/%02d-%s", MIGRATIONS_DIR, n, baseName(classpathFile));
  }

  private static String baseName(String classpathFile) {
    return classpathFile.substring(classpathFile.lastIndexOf('/') + 1);
  }
}
