package com.nexus.nexussync.params;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads an experiment on top of {@code default.yml} and validates the result (unit U1).
 *
 * <p>Both files are read as YAML trees and merged: scalars and nested objects of the experiment
 * override the defaults recursively, lists are replaced whole. {@code runtime.nexussync_dir}, when
 * absent, is the parent of the {@code params/} directory that holds the defaults. Unknown keys,
 * invalid enum names and out-of-range values raise {@link ParamsException} with the YAML path.
 */
public final class ParamsLoader {

  static final String RUNTIME_KEY = "runtime";
  static final String DIR_KEY = "nexussync_dir";
  private static final int LEVELS_ABOVE_DEFAULTS = 2;

  private static final ObjectMapper MAPPER =
      YAMLMapper.builder()
          .addModule(new Jdk8Module())
          .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build();

  private ParamsLoader() {}

  /**
   * Loads {@code experiment} merged on top of {@code defaults} and validates it.
   *
   * @param experiment YAML of the experiment; may declare only the keys that change
   * @param defaults complete YAML with every parameter, normally {@code params/default.yml}
   * @return the immutable, validated parameter set
   * @throws ParamsException if a file cannot be read or parsed, a key is unknown, an enum name is
   *     invalid, {@code runtime.nexussync_dir} cannot be resolved or a value is out of range
   * @implNote O(n) time and space in the number of YAML nodes of both files.
   */
  public static Params load(Path experiment, Path defaults) throws ParamsException {
    Path nexussyncDir = nexussyncDirOf(defaults);
    JsonNode merged = merge(readObject(defaults), readObject(experiment));
    fillNexussyncDir(merged, nexussyncDir);
    Params params = toParams(merged);
    ParamSpec.validate(params);
    return params;
  }

  /**
   * Deep merge of two YAML trees: objects merge key by key, anything else is replaced.
   *
   * @param base tree with the default values; it is not mutated
   * @param override tree whose values win; it is not mutated
   * @return a new tree
   * @implNote O(n) time and space in the number of nodes of both trees.
   */
  static JsonNode merge(JsonNode base, JsonNode override) {
    if (!(base instanceof ObjectNode baseObject) || !(override instanceof ObjectNode overObject)) {
      return override.deepCopy();
    }
    ObjectNode out = baseObject.deepCopy();
    for (Map.Entry<String, JsonNode> entry : overObject.properties()) {
      String key = entry.getKey();
      JsonNode value =
          out.has(key) ? merge(out.get(key), entry.getValue()) : entry.getValue().deepCopy();
      out.set(key, value);
    }
    return out;
  }

  private static Path nexussyncDirOf(Path defaults) throws ParamsException {
    Path dir = defaults.toAbsolutePath().normalize();
    for (int i = 0; i < LEVELS_ABOVE_DEFAULTS; i++) {
      dir = dir.getParent();
      if (dir == null) {
        throw new ParamsException(
            "no se puede resolver runtime."
                + DIR_KEY
                + " desde "
                + defaults
                + ": sin directorio padre");
      }
    }
    return dir;
  }

  private static ObjectNode readObject(Path file) throws ParamsException {
    JsonNode node;
    try {
      node = MAPPER.readTree(file.toFile());
    } catch (IOException e) {
      throw new ParamsException("no se pudo leer " + file + ": " + e.getMessage(), e);
    }
    if (node instanceof ObjectNode object) {
      return object;
    }
    throw new ParamsException(file + " no es un mapa YAML");
  }

  private static void fillNexussyncDir(JsonNode merged, Path nexussyncDir) throws ParamsException {
    if (!(merged.get(RUNTIME_KEY) instanceof ObjectNode runtime)) {
      throw new ParamsException(RUNTIME_KEY + ": sección ausente");
    }
    if (!runtime.hasNonNull(DIR_KEY)) {
      runtime.put(DIR_KEY, nexussyncDir.toString());
    }
  }

  private static Params toParams(JsonNode merged) throws ParamsException {
    try {
      return MAPPER.treeToValue(merged, Params.class);
    } catch (JsonMappingException e) {
      throw new ParamsException(pathOf(e) + ": " + e.getOriginalMessage(), e);
    } catch (JsonProcessingException e) {
      throw new ParamsException("parámetros inválidos: " + e.getOriginalMessage(), e);
    }
  }

  private static String pathOf(JsonMappingException e) {
    StringBuilder path = new StringBuilder();
    for (Reference ref : e.getPath()) {
      if (ref.getFieldName() == null) {
        path.append('[').append(ref.getIndex()).append(']');
      } else {
        if (path.length() > 0) {
          path.append('.');
        }
        path.append(ref.getFieldName());
      }
    }
    return path.length() == 0 ? "raíz" : path.toString();
  }
}
