package com.nexus.nexussync.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.params.ParamsException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldSetTest {

  @TempDir Path dir;

  static Path resource(String name) throws URISyntaxException {
    URL url = GoldSetTest.class.getResource("/nexussync/calibration/" + name);
    assertThat(url).as(name).isNotNull();
    return Path.of(url.toURI());
  }

  private Path write(String name, String yaml) throws IOException {
    return Files.writeString(dir.resolve(name), yaml, StandardCharsets.UTF_8);
  }

  // U13-01
  @Test
  void given_goldDir_when_load_then_oneEntryPerCoupleWithEveryField() throws Exception {
    Map<String, GoldEntry> gold = GoldSet.load(resource("gold"));

    assertThat(gold).containsOnlyKeys("101-102", "201-202");
    GoldEntry opuestos = gold.get("101-102");
    assertThat(opuestos.expectedTypes()).containsExactlyInAnyOrder("PARK", "MUSEUM");
    assertThat(opuestos.forbiddenTypes()).containsExactly("BAR");
    assertThat(opuestos.forbiddenIds()).containsExactly("act-999");
    assertThat(opuestos.notes()).contains("museo");
    GoldEntry hogar = gold.get("201-202");
    assertThat(hogar.expectedTypes()).containsExactly("HOME_COOKING");
    assertThat(hogar.forbiddenTypes()).isEmpty();
    assertThat(hogar.forbiddenIds()).isEmpty();
    assertThat(hogar.notes()).isEmpty();
  }

  // U13-01 (reviewed_by: optional key, absent or null = draft)
  @Test
  void given_reviewedByKey_when_load_then_reviewerKeptAndDraftsEmpty() throws Exception {
    write("a.yml", "couple_id: \"1-2\"\nexpected_types: [PARK]\nreviewed_by: \"Suzu\"\n");
    write("b.yml", "couple_id: \"3-4\"\nexpected_types: [PARK]\nreviewed_by: null\n");
    write("c.yml", "couple_id: \"5-6\"\nexpected_types: [PARK]\n");

    Map<String, GoldEntry> gold = GoldSet.load(dir);

    assertThat(gold.get("1-2").reviewedBy()).contains("Suzu");
    assertThat(gold.get("3-4").reviewedBy()).isEmpty();
    assertThat(gold.get("5-6").reviewedBy()).isEmpty();
    assertThat(new GoldEntry("7-8", Set.of("PARK"), Set.of(), Set.of(), "").reviewedBy()).isEmpty();
  }

  // U13-01
  @Test
  void given_coupleWithoutGold_when_load_then_absentWithoutError() throws Exception {
    assertThat(GoldSet.load(resource("gold"))).doesNotContainKey("301-302");
    assertThat(GoldSet.load(dir)).isEmpty();
  }

  // U13-01
  @Test
  void given_emptyExpectedTypes_when_load_then_paramsException() throws Exception {
    write("x.yml", "couple_id: \"1-2\"\nexpected_types: []\n");

    assertThatThrownBy(() -> GoldSet.load(dir))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("expected_types");
  }

  // U13-01
  @Test
  void given_missingExpectedTypes_when_load_then_paramsException() throws Exception {
    write("x.yml", "couple_id: \"1-2\"\nnotes: null\n");

    assertThatThrownBy(() -> GoldSet.load(dir)).isInstanceOf(ParamsException.class);
  }

  @Test
  void given_invalidFiles_when_load_then_paramsExceptionForEach() throws Exception {
    List<String> invalid =
        List.of(
            "expected_types: [PARK]\n",
            "couple_id: \"  \"\nexpected_types: [PARK]\n",
            "couple_id: 12\nexpected_types: [PARK]\n",
            "couple_id: \"1-2\"\nexpected_types: [PARK]\ncolour: red\n",
            "couple_id: \"1-2\"\nexpected_types: PARK\n",
            "couple_id: \"1-2\"\nexpected_types: [[PARK]]\n",
            "couple_id: \"1-2\"\nexpected_types: [null]\n",
            "couple_id: \"1-2\"\nexpected_types: [PARK]\nreviewed_by: \" \"\n",
            "couple_id: \"1-2\"\nexpected_types: [PARK]\nreviewed_by: [Suzu]\n",
            "- just\n- a list\n",
            "couple_id: [unclosed\n");
    for (String yaml : invalid) {
      Path sub = Files.createDirectories(dir.resolve("case" + invalid.indexOf(yaml)));
      Files.writeString(sub.resolve("g.yml"), yaml, StandardCharsets.UTF_8);

      assertThatThrownBy(() -> GoldSet.load(sub)).as(yaml).isInstanceOf(ParamsException.class);
    }
  }

  @Test
  void given_twoFilesForSameCouple_when_load_then_duplicateError() throws Exception {
    write("a.yml", "couple_id: \"1-2\"\nexpected_types: [PARK]\n");
    write("b.yml", "couple_id: \"1-2\"\nexpected_types: [BAR]\n");

    assertThatThrownBy(() -> GoldSet.load(dir))
        .isInstanceOf(ParamsException.class)
        .hasMessageContaining("duplicate");
  }

  @Test
  void given_missingDirOrEmptyFile_when_load_then_paramsException() throws Exception {
    assertThatThrownBy(() -> GoldSet.load(dir.resolve("nope"))).isInstanceOf(ParamsException.class);
    write("empty.yml", "");
    assertThatThrownBy(() -> GoldSet.load(dir)).isInstanceOf(ParamsException.class);
  }

  @Test
  void given_directoryNamedYml_when_load_then_ignored() throws Exception {
    Files.createDirectories(dir.resolve("sub.yml"));
    write("g.yml", "couple_id: \"1-2\"\nexpected_types: [PARK, 3]\nforbidden_ids: [x]\n");

    GoldEntry e = GoldSet.load(dir).get("1-2");

    assertThat(e.expectedTypes()).containsExactlyInAnyOrder("PARK", "3");
    assertThat(e.forbiddenIds()).containsExactly("x");
  }

  @Test
  void given_emptyExpectedTypes_when_constructEntry_then_illegalArgument() {
    assertThatThrownBy(() -> new GoldEntry("1-2", Set.of(), Set.of(), Set.of(), ""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
