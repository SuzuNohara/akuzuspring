package com.nexus.service.recommendation.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nexus.config.AgentProperties;
import com.nexus.config.AgentProperties.TimePolicy;
import com.nexus.entity.Activity;
import com.nexus.exception.AgentTimeoutException;
import com.nexus.exception.AgentUnavailableException;
import com.nexus.integration.AgentTestProperties;
import com.nexus.integration.ollama.ChatMessage;
import com.nexus.integration.ollama.ChatResult;
import com.nexus.integration.ollama.OllamaClient;
import com.nexus.model.IdeaCategory;
import com.nexus.model.ReasonSource;
import com.nexus.model.Scope;
import com.nexus.service.recommendation.Candidate;
import com.nexus.service.recommendation.CoupleContext;
import com.nexus.service.recommendation.MoodLevel;
import com.nexus.service.recommendation.PlaceChoice;
import com.nexus.service.recommendation.RecommendationQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.slf4j.LoggerFactory;

/** [t06b-agente-java] U12 - Compositor: modos, relleno por reglas, presupuesto, guardia y traza. */
class RecommendationComposerTest {

    private static final Instant NIGHT = Instant.parse("2026-10-24T02:00:00Z");
    private static final String GOOD = "Una tarde tranquila para platicar sin prisa.";

    private final OllamaClient ollama = mock(OllamaClient.class);
    private final AtomicLong clock = new AtomicLong(0);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger composerLog = (Logger) LoggerFactory.getLogger(RecommendationComposer.class);

    @BeforeEach
    void captureLog() {
        logs.start();
        composerLog.addAppender(logs);
    }

    @AfterEach
    void releaseLog() {
        composerLog.detachAppender(logs);
    }

    private RecommendationComposer composer(boolean enabled, TimePolicy policy) {
        AgentProperties props = AgentTestProperties.of(enabled, true, Duration.ofSeconds(7), policy);
        return new RecommendationComposer(ollama, props);
    }

    private RecommendationComposer composer() {
        return composer(true, TimePolicy.HARD);
    }

    private Deadline deadline() {
        return Deadline.start(9_000, clock::get);
    }

    private void advanceMs(long ms) {
        clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(ms));
    }

    private static List<Candidate> pool(int n) {
        return LongStream.rangeClosed(1, n)
                .mapToObj(id -> new Candidate(
                        Activity.builder().id(id).title("Secreto " + id).description("d").activityType("FILM")
                                .rationale("Frase del catalogo " + id + ".").costMxnPp(0).durationAvg(60)
                                .outdoor(false).build(),
                        null,
                        1.0 - id * 0.01))
                .toList();
    }

    private static RecommendationQuery query() {
        return new RecommendationQuery(Scope.ANY, 500, IdeaCategory.SOCIAL, NIGHT, NIGHT.plusSeconds(7200), List.of());
    }

    private static CoupleContext context() {
        return new CoupleContext(Map.of(IdeaCategory.SOCIAL, 2), MoodLevel.BAJO, MoodLevel.ALTO,
                List.of("Cinefilia"), List.of("Senderismo"));
    }

    private static ChatResult answer(String content) {
        return new ChatResult(content, "stop", 800, 90, 1_500);
    }

    private static String picks(int... refs) {
        List<String> items = new ArrayList<>();
        for (int ref : refs) {
            items.add("{\"ref\":" + ref + ",\"why\":\"" + GOOD + "\"}");
        }
        return "{\"picks\":[" + String.join(",", items) + "]}";
    }

    private void ollamaAnswers(ChatResult... results) {
        var stub = when(ollama.chat(any(), any(), any(), any()));
        for (ChatResult r : results) {
            stub = stub.thenReturn(r);
        }
    }

    private Composition compose(List<Candidate> pool) {
        return composer().compose(query(), context(), pool, 15, deadline());
    }

    @Test
    @DisplayName("U12.2: should give LLM with 5 agent reasons and 10 catalog ones by score")
    void shouldGiveLlmWithFiveAgentReasonsAndTenByRules() {
        ollamaAnswers(answer(picks(3, 1, 5, 2, 6)));

        Composition c = compose(pool(20));

        assertThat(c.trace().mode()).isEqualTo(ComposeMode.LLM);
        assertThat(c.trace().attempts()).isEqualTo(1);
        assertThat(c.picks()).hasSize(15);
        assertThat(c.picks().subList(0, 5)).extracting(p -> p.candidate().activity().getId())
                .containsExactly(3L, 1L, 5L, 2L, 6L);
        assertThat(c.picks().subList(0, 5)).allMatch(p -> p.source() == ReasonSource.AGENT);
        assertThat(c.picks().subList(5, 15)).extracting(p -> p.candidate().activity().getId())
                .containsExactly(4L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);
        assertThat(c.picks().subList(5, 15)).allMatch(p -> p.source() == ReasonSource.CATALOG && p.ref() == 0);
    }

    @Test
    @DisplayName("U12.2: should give LLM_FILLED with 2 valid picks and 13 by rules")
    void shouldGiveLlmFilledWithTwoValidPicks() {
        ollamaAnswers(answer(picks(9, 4)));

        Composition c = compose(pool(20));

        assertThat(c.trace().mode()).isEqualTo(ComposeMode.LLM_FILLED);
        assertThat(c.picks()).hasSize(15);
        assertThat(c.picks()).filteredOn(p -> p.source() == ReasonSource.AGENT).hasSize(2);
    }

    @Test
    @DisplayName("U12.2: should retry once without echoing the bad answer")
    void shouldRetryOnceWithoutEchoingTheBadAnswer() {
        ollamaAnswers(answer("{\"picks\": [ roto"), answer(picks(1, 2, 3, 4, 5)));

        Composition c = compose(pool(20));

        assertThat(c.trace().mode()).isEqualTo(ComposeMode.LLM);
        assertThat(c.trace().attempts()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(ollama, times(2)).chat(messages.capture(), any(), any(), any());
        List<ChatMessage> second = messages.getAllValues().get(1);
        assertThat(second).extracting(ChatMessage::role).containsExactly("system", "user", "user");
        assertThat(second).noneMatch(m -> m.content().contains("roto"));
        assertThat(second.get(2).content()).contains("no fue JSON válido");
    }

    @Test
    @DisplayName("U12.2: should give RULES_INVALID_OUTPUT after two broken answers")
    void shouldGiveRulesInvalidOutputAfterTwoBrokenAnswers() {
        ollamaAnswers(answer("nada"), answer("{\"picks\":[]}"));

        Composition c = compose(pool(20));

        assertThat(c.trace().mode()).isEqualTo(ComposeMode.RULES_INVALID_OUTPUT);
        assertThat(c.picks()).hasSize(15).allMatch(p -> p.source() == ReasonSource.CATALOG);
    }

    @Test
    @DisplayName("U12.2: should count done_reason=length as an invalid answer")
    void shouldCountLengthAsAnInvalidAnswer() {
        ChatResult cut = new ChatResult(picks(1, 2, 3, 4, 5), "length", 800, 600, 3_000);
        ollamaAnswers(cut, cut);

        Composition c = compose(pool(20));

        assertThat(c.trace().mode()).isEqualTo(ComposeMode.RULES_INVALID_OUTPUT);
        assertThat(c.trace().doneReason()).isEqualTo("length");
        assertThat(c.trace().evalTokens()).isEqualTo(1_200);
    }

    @Test
    @DisplayName("U12.2: should give RULES_TIMEOUT without retrying")
    void shouldGiveRulesTimeoutWithoutRetrying() {
        when(ollama.chat(any(), any(), any(), any())).thenThrow(new AgentTimeoutException("tarde", null));

        Composition c = compose(pool(20));

        assertThat(c.trace().mode()).isEqualTo(ComposeMode.RULES_TIMEOUT);
        assertThat(c.trace().attempts()).isEqualTo(1);
        assertThat(c.picks()).hasSize(15);
        verify(ollama, times(1)).chat(any(), any(), any(), any());
    }

    @Test
    @DisplayName("U12.2: should give RULES_UNAVAILABLE when Ollama is down")
    void shouldGiveRulesUnavailableWhenOllamaIsDown() {
        when(ollama.chat(any(), any(), any(), any())).thenThrow(new AgentUnavailableException("caido"));

        Composition c = compose(pool(20));

        assertThat(c.trace().mode()).isEqualTo(ComposeMode.RULES_UNAVAILABLE);
        assertThat(c.picks()).hasSize(15);
    }

    @Test
    @DisplayName("U12.2: should not call Mistral with less than 2500 ms left (RULES_NO_BUDGET)")
    void shouldNotCallMistralWithoutBudget() {
        Deadline deadline = deadline();
        advanceMs(6_600);

        Composition c = composer().compose(query(), context(), pool(20), 15, deadline);

        assertThat(c.trace().mode()).isEqualTo(ComposeMode.RULES_NO_BUDGET);
        assertThat(c.trace().attempts()).isZero();
        assertThat(c.picks()).hasSize(15);
        verify(ollama, never()).chat(any(), any(), any(), any());
    }

    @Test
    @DisplayName("U12.2: should give NO_CANDIDATES and an empty list without candidates")
    void shouldGiveNoCandidatesAndAnEmptyList() {
        Composition c = compose(List.of());

        assertThat(c.trace().mode()).isEqualTo(ComposeMode.NO_CANDIDATES);
        assertThat(c.picks()).isEmpty();
        verify(ollama, never()).chat(any(), any(), any(), any());
    }

    @Test
    @DisplayName("U12.2: should give DISABLED and everything by rules with the agent off")
    void shouldGiveDisabledWithTheAgentOff() {
        Composition c = composer(false, TimePolicy.HARD).compose(query(), context(), pool(20), 15, deadline());

        assertThat(c.trace().mode()).isEqualTo(ComposeMode.DISABLED);
        assertThat(c.picks()).hasSize(15).allMatch(p -> p.source() == ReasonSource.CATALOG);
        verify(ollama, never()).chat(any(), any(), any(), any());
    }

    @Test
    @DisplayName("U12.3: should give 8 recommendations with 8 candidates, not 15")
    void shouldGiveEightWithEightCandidates() {
        ollamaAnswers(answer(picks(1, 2, 3, 4, 5)));

        Composition c = compose(pool(8));

        assertThat(c.picks()).hasSize(8);
        assertThat(c.picks()).extracting(p -> p.candidate().activity().getId()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("U12.4: should fall back to rules when the guard finds a repeated activity, without throwing")
    void shouldFallBackToRulesWhenTheGuardFindsARepeatedActivity() {
        Activity same = pool(1).get(0).activity();
        List<Candidate> pool = new ArrayList<>(List.of(
                new Candidate(same, new PlaceChoice(10L, "Lugar 10", "Coyoacán", 1.0), 0.99),
                new Candidate(same, new PlaceChoice(11L, "Lugar 11", "Coyoacán", 2.0), 0.98)));
        pool.addAll(pool(20).subList(1, 20));
        ollamaAnswers(answer(picks(1, 2, 3, 4, 5)));

        Composition c = compose(pool);

        assertThat(c.trace().mode()).isEqualTo(ComposeMode.RULES_INVALID_OUTPUT);
        assertThat(c.picks()).hasSize(15).allMatch(p -> p.source() == ReasonSource.CATALOG);
        assertThat(c.picks()).extracting(p -> p.candidate().activity().getId()).doesNotHaveDuplicates();
        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("actividad repetida"));
    }

    @Test
    @DisplayName("U12.5: should log the 13 fields of the trace without titles, reasons nor preferences")
    void shouldLogTheTraceWithoutPersonalData() {
        ollamaAnswers(answer(picks(3, 1, 5, 2, 6)));

        compose(pool(20));

        String line = logs.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.startsWith("compose ")).findFirst().orElseThrow();
        assertThat(line).contains("mode=LLM", "attempts=1", "prompt_version=v1", "prompt_level=full",
                "model=mistral:7b", "time_policy=HARD", "llm_ms=1500", "over_budget_ms=0", "prompt_tokens=800",
                "eval_tokens=90", "dropped=0", "replaced=0", "done_reason=stop");
        assertThat(line.split(" ")).hasSize(14);
        assertThat(line).doesNotContain("Secreto", "Frase del catalogo", GOOD, "Cinefilia", "Senderismo", "ánimo");
    }

    @Test
    @DisplayName("U12.6: should let Mistral finish past the budget with SOFT, capped at 60 s, and record it")
    void shouldLetMistralFinishPastTheBudgetWithSoft() {
        when(ollama.chat(any(), any(), any(), any())).thenAnswer((Answer<ChatResult>) call -> {
            advanceMs(12_000);
            return answer(picks(1, 2, 3, 4, 5));
        });

        Composition c = composer(true, TimePolicy.SOFT).compose(query(), context(), pool(20), 15, deadline());

        assertThat(c.trace().mode()).isEqualTo(ComposeMode.LLM);
        assertThat(c.trace().overBudgetMs()).isEqualTo(3_000);
        assertThat(c.trace().timePolicy()).isEqualTo("SOFT");
        verify(ollama).chat(any(), any(), any(), org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("U12.1: should cap the call at min(7 s, remaining - 300 ms) with HARD")
    void shouldCapTheCallWithHard() {
        Deadline deadline = deadline();
        advanceMs(4_000);
        ollamaAnswers(answer(picks(1, 2, 3, 4, 5)));

        composer().compose(query(), context(), pool(20), 15, deadline);

        verify(ollama).chat(any(), any(), any(), org.mockito.ArgumentMatchers.eq(Duration.ofMillis(4_700)));
    }

    @Test
    @DisplayName("U12: should fill by rules in the selection order, even with scores on another scale")
    void shouldFillByRulesInTheSelectionOrder() {
        List<Candidate> mixed = new ArrayList<>(pool(4));
        Activity fromCatalog = Activity.builder().id(50L).title("Catalogo").rationale("Frase del catalogo 50.").build();
        mixed.add(new Candidate(fromCatalog, null, 3.0));
        ollamaAnswers(answer("nada"), answer("nada"));

        Composition c = compose(mixed);

        assertThat(c.picks()).extracting(p -> p.candidate().activity().getId()).containsExactly(1L, 2L, 3L, 4L, 50L);
    }

    @Test
    @DisplayName("U12: should keep a replaced reason on the picked candidate and still pass the guard")
    void shouldKeepAReplacedReasonOnThePickedCandidate() {
        ollamaAnswers(answer("{\"picks\":[{\"ref\":2,\"why\":\"Ideal porque los dos están cansados hoy.\"},"
                + "{\"ref\":1,\"why\":\"" + GOOD + "\"}]}"));

        Composition c = compose(pool(20));

        assertThat(c.trace().mode()).isEqualTo(ComposeMode.LLM_FILLED);
        assertThat(c.trace().replaced()).isEqualTo(1);
        assertThat(c.picks().get(0).reason()).isEqualTo("Frase del catalogo 2.");
        assertThat(c.picks().get(0).ref()).isEqualTo(2);
    }
}
