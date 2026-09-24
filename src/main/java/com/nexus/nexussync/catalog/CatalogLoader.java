package com.nexus.nexussync.catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Reads the catalog CSV files of the knowledge base into {@link Activity}, {@link Place} and link
 * maps (§3.2).
 *
 * <p>Columns are addressed by header name, so their order and any extra column are irrelevant.
 * Files are UTF-8; a leading byte order mark is tolerated. Every failure (missing file, missing
 * required column, non numeric value, unknown enum value, duplicated identifier, dangling link) is
 * reported as a {@link CatalogException} naming the file, the row (header = row 1) and the column.
 */
public final class CatalogLoader {

  private static final CSVFormat FORMAT =
      CSVFormat.RFC4180
          .builder()
          .setHeader()
          .setSkipHeaderRecord(true)
          .setTrim(true)
          .setIgnoreEmptyLines(true)
          .build();

  /** Byte order mark that some editors prepend to UTF-8 files; tolerated on the first header. */
  private static final char BOM = 0xFEFF;

  /** commons-csv numbers data records from 1; the header is row 1 for humans and tools/kb. */
  private static final long HEADER_ROWS = 1;

  private static final String TRUE_FLAG = "1";
  private static final String ACTIVITY_ID = "activity_id";
  private static final String PLACE_ID = "place_id";
  private static final String COST_OVERRIDE = "cost_override";

  /** File names inside the catalog directory (kb/schema.md §2.2). */
  private static final String ACTIVITIES_FILE = "activities.csv";

  private static final String LINKS_FILE = "activity_places.csv";

  private CatalogLoader() {}

  /**
   * Loads the whole catalog: {@code activities.csv} and {@code activity_places.csv} from {@code
   * catalogDir}, places from {@code placesCsv}, and checks that every link points to a known
   * activity and a known place.
   *
   * @param catalogDir directory holding {@code activities.csv} and {@code activity_places.csv}
   * @param placesCsv path of the places file (usually {@code kb/places.csv})
   * @return the three tables indexed by slug
   * @throws CatalogException on any read, structure, value or integrity error
   * @implNote O(a + p + l) time and space, a = activities, p = places, l = link rows.
   */
  public static Catalog load(Path catalogDir, Path placesCsv) throws CatalogException {
    Map<String, Activity> activities = loadActivities(catalogDir.resolve(ACTIVITIES_FILE));
    Map<String, Place> places = loadPlaces(placesCsv);
    Map<String, List<String>> links = loadLinks(catalogDir.resolve(LINKS_FILE), activities, places);
    return new Catalog(activities, places, links);
  }

  /**
   * Loads {@code places.csv}.
   *
   * @param csv path of the places file
   * @return places by {@code place_id}, unmodifiable, in file order
   * @throws CatalogException on any read, structure or value error
   * @implNote O(n) time and space, n = rows of the file.
   */
  public static Map<String, Place> loadPlaces(Path csv) throws CatalogException {
    return readTable(csv, PLACE_ID, CatalogLoader::toPlace);
  }

  private static Place toPlace(Row row) throws CatalogException {
    String hours = row.text("opening_hours");
    return new Place(
        row.text(PLACE_ID),
        row.text("name"),
        row.text("place_type"),
        row.decimal("lat"),
        row.decimal("lon"),
        row.text("borough"),
        hours.isEmpty() ? Optional.empty() : Optional.of(hours),
        row.flag("is_outdoor"),
        !row.text("verified_by").isEmpty());
  }

  /**
   * Loads {@code activity_places.csv} and checks it against the activities and places already
   * loaded: every {@code activity_id} and {@code place_id} must exist and no pair may repeat.
   *
   * @param csv path of the links file
   * @param activities activities by slug, as returned by {@link #loadActivities(Path)}
   * @param places places by slug, as returned by {@link #loadPlaces(Path)}
   * @return place ids by {@code activity_id}, unmodifiable, both in file order; activities without
   *     links are absent
   * @throws CatalogException on any read, structure, value or integrity error, naming the row
   * @implNote O(l) time and space, l = link rows.
   */
  public static Map<String, List<String>> loadLinks(
      Path csv, Map<String, Activity> activities, Map<String, Place> places)
      throws CatalogException {
    Map<String, List<String>> out = new LinkedHashMap<>();
    Set<List<String>> pairs = new HashSet<>();
    for (Row row : readRows(csv)) {
      Link link = toLink(row);
      if (!activities.containsKey(link.activityId())) {
        throw row.error(ACTIVITY_ID, "unknown activity '" + link.activityId() + "'");
      }
      if (!places.containsKey(link.placeId())) {
        throw row.error(PLACE_ID, "unknown place '" + link.placeId() + "'");
      }
      if (!pairs.add(List.of(link.activityId(), link.placeId()))) {
        throw row.error(
            PLACE_ID, "duplicated pair '" + link.activityId() + "', '" + link.placeId() + "'");
      }
      out.computeIfAbsent(link.activityId(), key -> new ArrayList<>()).add(link.placeId());
    }
    out.replaceAll((key, ids) -> Collections.unmodifiableList(ids));
    return Collections.unmodifiableMap(out);
  }

  /**
   * Reads {@code activity_places.csv} row by row without integrity checks.
   *
   * @param csv path of the links file
   * @return one {@link Link} per data row, in file order
   * @throws CatalogException on any read, structure or value error
   * @implNote O(l) time and space, l = link rows.
   */
  static List<Link> readLinks(Path csv) throws CatalogException {
    List<Link> out = new ArrayList<>();
    for (Row row : readRows(csv)) {
      out.add(toLink(row));
    }
    return Collections.unmodifiableList(out);
  }

  private static Link toLink(Row row) throws CatalogException {
    return new Link(row.text(ACTIVITY_ID), row.text(PLACE_ID), row.optionalInteger(COST_OVERRIDE));
  }

  /**
   * Loads {@code activities.csv}.
   *
   * @param csv path of the activities file
   * @return activities by {@code activity_id}, unmodifiable, in file order
   * @throws CatalogException on any read, structure or value error
   * @implNote O(n) time and space, n = rows of the file.
   */
  public static Map<String, Activity> loadActivities(Path csv) throws CatalogException {
    return readTable(csv, ACTIVITY_ID, CatalogLoader::toActivity);
  }

  private static Activity toActivity(Row row) throws CatalogException {
    return new Activity(
        row.text(ACTIVITY_ID),
        row.text("title"),
        row.text("activity_type"),
        row.enumValue("location_scope", LocationScope.class),
        row.set("place_types"),
        row.set("interests"),
        row.enumSet("dayparts", Daypart.class),
        row.set("seasons"),
        row.integer("duration_min"),
        row.integer("duration_avg"),
        row.integer("duration_max"),
        row.integer("difficulty_physical"),
        row.integer("difficulty_mental"),
        row.integer("collaboration"),
        row.integer("preparation"),
        row.integer("cost_mxn_pp"),
        row.text("price_band"),
        row.flag("is_outdoor"),
        row.set("weather_ok"),
        row.set("ambience"),
        row.text("description"));
  }

  private static <T> Map<String, T> readTable(Path csv, String idColumn, RowMapper<T> mapper)
      throws CatalogException {
    Map<String, T> out = new LinkedHashMap<>();
    for (Row row : readRows(csv)) {
      String id = row.text(idColumn);
      if (id.isEmpty()) {
        throw row.error(idColumn, "empty identifier");
      }
      if (out.containsKey(id)) {
        throw row.error(idColumn, "duplicated identifier '" + id + "'");
      }
      out.put(id, mapper.map(row));
    }
    return Collections.unmodifiableMap(out);
  }

  private static List<Row> readRows(Path csv) throws CatalogException {
    try (CSVParser parser = CSVParser.parse(csv, StandardCharsets.UTF_8, FORMAT)) {
      Header header = Header.of(csv, parser.getHeaderNames());
      List<Row> rows = new ArrayList<>();
      for (CSVRecord record : parser) {
        rows.add(new Row(header, record.getRecordNumber() + HEADER_ROWS, record.toList()));
      }
      return rows;
    } catch (IOException | UncheckedIOException ex) {
      throw new CatalogException(csv.getFileName() + ": cannot read (" + ex.getMessage() + ")", ex);
    }
  }

  /** Maps one parsed row to a catalog record. */
  @FunctionalInterface
  private interface RowMapper<T> {
    T map(Row row) throws CatalogException;
  }

  /** Column index of a file by header name; the first name is stripped of a leading BOM. */
  private record Header(Path file, Map<String, Integer> index) {

    static Header of(Path file, List<String> names) {
      Map<String, Integer> index = new HashMap<>();
      for (int i = 0; i < names.size(); i++) {
        String name = names.get(i);
        if (i == 0 && !name.isEmpty() && name.charAt(0) == BOM) {
          name = name.substring(1);
        }
        index.putIfAbsent(name, i);
      }
      return new Header(file, Map.copyOf(index));
    }

    int require(String column) throws CatalogException {
      Integer position = index.get(column);
      if (position == null) {
        throw new CatalogException(
            file.getFileName() + ": row 1: column '" + column + "': missing required column");
      }
      return position;
    }
  }

  /** One data row: typed accessors by column name that fail with file, row and column. */
  private record Row(Header header, long number, List<String> values) {

    String text(String column) throws CatalogException {
      int position = header.require(column);
      return position < values.size() ? values.get(position) : "";
    }

    int integer(String column) throws CatalogException {
      String raw = text(column);
      try {
        return Integer.parseInt(raw);
      } catch (NumberFormatException ex) {
        throw error(column, "expected an integer, got '" + raw + "'", ex);
      }
    }

    OptionalInt optionalInteger(String column) throws CatalogException {
      return text(column).isEmpty() ? OptionalInt.empty() : OptionalInt.of(integer(column));
    }

    double decimal(String column) throws CatalogException {
      String raw = text(column);
      try {
        return Double.parseDouble(raw);
      } catch (NumberFormatException ex) {
        throw error(column, "expected a decimal number, got '" + raw + "'", ex);
      }
    }

    boolean flag(String column) throws CatalogException {
      return TRUE_FLAG.equals(text(column));
    }

    Set<String> set(String column) throws CatalogException {
      return CsvSets.parse(text(column));
    }

    <E extends Enum<E>> E enumValue(String column, Class<E> type) throws CatalogException {
      return enumOf(column, type, text(column));
    }

    <E extends Enum<E>> Set<E> enumSet(String column, Class<E> type) throws CatalogException {
      EnumSet<E> out = EnumSet.noneOf(type);
      for (String raw : set(column)) {
        out.add(enumOf(column, type, raw));
      }
      return out;
    }

    private <E extends Enum<E>> E enumOf(String column, Class<E> type, String raw)
        throws CatalogException {
      try {
        return Enum.valueOf(type, raw);
      } catch (IllegalArgumentException ex) {
        String allowed = Arrays.toString(type.getEnumConstants());
        throw error(column, "expected one of " + allowed + ", got '" + raw + "'", ex);
      }
    }

    CatalogException error(String column, String message) {
      return new CatalogException(prefix(column) + message);
    }

    CatalogException error(String column, String message, Throwable cause) {
      return new CatalogException(prefix(column) + message, cause);
    }

    private String prefix(String column) {
      return header.file().getFileName() + ": row " + number + ": column '" + column + "': ";
    }
  }
}
