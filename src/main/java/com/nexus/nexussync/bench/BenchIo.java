package com.nexus.nexussync.bench;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexus.nexussync.NexussyncException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serialization shared by the bench (unit U11): one JSON mapper for the traces and the run record,
 * one YAML mapper for the canonical {@code params.yml} and one for {@code weights.yml}.
 *
 * <p>The JSON mapper orders map entries by key and writes dates as ISO text, so equal inputs give
 * byte-identical files; the same mapper reads {@code record.json} back. The canonical YAML mirrors
 * the canonical form behind {@code Params.hash()}: snake_case keys sorted alphabetically, map
 * entries sorted, paths as plain text.
 */
final class BenchIo {

  /** Traces and run record. */
  static final ObjectMapper JSON =
      JsonMapper.builder()
          .addModule(new Jdk8Module())
          .addModule(new JavaTimeModule())
          .addModule(new SimpleModule().addSerializer(Path.class, ToStringSerializer.instance))
          .enable(SerializationFeature.INDENT_OUTPUT)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  /** Canonical YAML of the parameters. */
  static final ObjectMapper CANONICAL_YAML =
      YAMLMapper.builder()
          .addModule(new Jdk8Module())
          .addModule(new SimpleModule().addSerializer(Path.class, ToStringSerializer.instance))
          .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .build();

  /** Weights, in the same shape as {@code WeightStore}. */
  static final ObjectMapper WEIGHTS_YAML = new YAMLMapper();

  private BenchIo() {}

  /**
   * Writes {@code value} to {@code file} with {@code mapper}, creating the parent directories.
   *
   * @param mapper mapper to use
   * @param file target file, overwritten if present
   * @param value value to serialize
   * @throws NexussyncException if the file cannot be written
   * @implNote O(n) time and space in the size of the serialized value.
   */
  static void write(ObjectMapper mapper, Path file, Object value) throws NexussyncException {
    try {
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      mapper.writeValue(file.toFile(), value);
    } catch (IOException e) {
      throw new NexussyncException("cannot write " + file, e);
    }
  }
}
