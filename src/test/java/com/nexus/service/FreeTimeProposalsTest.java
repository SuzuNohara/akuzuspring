package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.service.FreeTimeNotifier.FreeSlot;
import com.nexus.service.FreeTimeProposals.Label;
import com.nexus.service.FreeTimeProposals.Window;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * [libres-juntos] La regla de propuestas con los mismos casos que la app
 * (utils/__fixtures__/free-proposals.json en el front; aqui, una copia igual).
 */
class FreeTimeProposalsTest {

    private static JsonNode cases() throws Exception {
        try (InputStream in = FreeTimeProposalsTest.class.getResourceAsStream("/free-proposals.json")) {
            return new ObjectMapper().readTree(in).get("cases");
        }
    }

    private static List<FreeSlot> slots(JsonNode days) {
        List<FreeSlot> out = new ArrayList<>();
        for (JsonNode d : days) {
            for (JsonNode s : d.get("mutualFreeSlots")) {
                out.add(new FreeSlot(LocalDate.parse(d.get("date").asText()),
                        LocalTime.parse(s.get("start").asText()), LocalTime.parse(s.get("end").asText())));
            }
        }
        return out;
    }

    private static List<String> asText(List<Window> windows) {
        return windows.stream().map(w -> w.date() + " " + w.start() + "-" + w.end() + " " + w.part() + " " + w.minutes()).toList();
    }

    private static List<String> expected(JsonNode windows) {
        List<String> out = new ArrayList<>();
        for (JsonNode w : windows) {
            out.add(w.get("date").asText() + " " + w.get("start").asText() + "-" + w.get("end").asText() + " "
                    + w.get("part").asText() + " " + w.get("minutes").asInt());
        }
        return out;
    }

    @TestFactory
    Stream<DynamicTest> sharedCases() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : cases()) {
            tests.add(DynamicTest.dynamicTest(c.get("name").asText(), () -> {
                LocalDateTime now = LocalDateTime.parse(c.get("now").asText());
                List<Window> windows = FreeTimeProposals.windowsOf(slots(c.get("days")), now, true);
                if (c.has("windows")) {
                    assertThat(asText(windows)).isEqualTo(expected(c.get("windows")));
                }
                if (c.has("unsplit")) {
                    assertThat(asText(FreeTimeProposals.windowsOf(slots(c.get("days")), now, false)))
                            .isEqualTo(expected(c.get("unsplit")));
                }
                List<String> proposals = new ArrayList<>();
                for (Window w : FreeTimeProposals.proposalsOf(windows, 3)) {
                    Label l = FreeTimeProposals.label(w, now.toLocalDate());
                    proposals.add(w.date() + " " + w.start() + "-" + w.end() + " " + l.when() + " · " + l.length());
                }
                List<String> want = new ArrayList<>();
                for (JsonNode p : c.get("proposals")) {
                    want.add(p.get("date").asText() + " " + p.get("start").asText() + "-" + p.get("end").asText() + " "
                            + p.get("when").asText() + " · " + p.get("length").asText());
                }
                assertThat(proposals).isEqualTo(want);
            }));
        }
        return tests.stream();
    }
}
