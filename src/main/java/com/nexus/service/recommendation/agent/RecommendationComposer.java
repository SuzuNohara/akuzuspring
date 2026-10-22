package com.nexus.service.recommendation.agent;

import com.nexus.config.AgentProperties;
import com.nexus.config.AgentProperties.TimePolicy;
import com.nexus.exception.AgentTimeoutException;
import com.nexus.exception.AgentUnavailableException;
import com.nexus.integration.ollama.ChatMessage;
import com.nexus.integration.ollama.ChatOptions;
import com.nexus.integration.ollama.ChatResult;
import com.nexus.integration.ollama.OllamaClient;
import com.nexus.model.ReasonSource;
import com.nexus.service.recommendation.Candidate;
import com.nexus.service.recommendation.CatalogReasonWriter;
import com.nexus.service.recommendation.CoupleContext;
import com.nexus.service.recommendation.RecommendationQuery;
import com.nexus.service.recommendation.agent.FinalListGuard.GuardException;
import com.nexus.service.recommendation.agent.PickValidator.ValidationResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * [t06b-agente-java] U12 - Arma la lista final (igual que {@code composer.py} del lab): Mistral elige
 * y justifica k ({@code llm-picks}) de las candidatas y las reglas completan hasta {@code expected}
 * en el orden de la seleccion con la frase del catalogo. Si Mistral no sirve (tiempo, caida, respuesta invalida o
 * sin presupuesto), toda la lista sale por reglas.
 *
 * <p>La guardia final corre en todos los modos; si la lista de Mistral no la pasa, sale la de reglas
 * con un aviso en el log, nunca un error para quien pide (R10). La traza va al log como
 * {@code clave=valor} y sin textos ni ids (R4).
 */
@Slf4j
@Service
public class RecommendationComposer {

    /** Version de las plantillas del prompt. */
    public static final String PROMPT_VERSION = "v1";

    /** Nivel del {@code system} del lab en que se basa la plantilla. */
    static final String PROMPT_LEVEL = "full";

    /** Tope de cada llamada con politica SOFT. */
    static final Duration SOFT_CAP = Duration.ofSeconds(60);

    private static final String LENGTH = "length";

    private final OllamaClient ollama;
    private final AgentProperties properties;
    private final PromptTemplate template;

    public RecommendationComposer(OllamaClient ollama, AgentProperties properties) {
        this.ollama = ollama;
        this.properties = properties;
        this.template = PromptTemplate.load(PROMPT_VERSION);
    }

    /** Una composicion en curso: lo que se pidio y los contadores de los intentos. */
    private static final class Run {
        private final RecommendationQuery query;
        private final CoupleContext context;
        private final List<Candidate> pool;
        private final int expected;
        private final int k;
        private final Deadline deadline;
        private int attempts;
        private long llmMs;
        private int promptTokens;
        private int evalTokens;
        private int dropped;
        private int replaced;
        private String doneReason = "";

        private Run(RecommendationQuery query, CoupleContext context, List<Candidate> pool, int expected, int k,
                    Deadline deadline) {
            this.query = query;
            this.context = context;
            this.pool = pool;
            this.expected = expected;
            this.k = k;
            this.deadline = deadline;
        }

        private void add(ChatResult result) {
            llmMs += result.wallMs();
            promptTokens += result.promptTokens();
            evalTokens += result.evalTokens();
            doneReason = result.doneReason() == null ? "" : result.doneReason();
        }
    }

    private record Outcome(ComposeMode mode, List<WrittenPick> picks) {}

    /**
     * @param pool candidatas en el orden de la seleccion; Mistral las ve todas numeradas
     * @param expected cuantas se quieren (15); salen menos si no hay tantas actividades
     * @param deadline presupuesto de la solicitud, creado antes de la seleccion
     * @implNote O(n log n) mas a lo mas {@code max-attempts} llamadas a Mistral.
     */
    public Composition compose(
            RecommendationQuery q, CoupleContext c, List<Candidate> pool, int expected, Deadline deadline) {
        int size = Math.min(expected, distinctActivities(pool));
        int k = Math.min(properties.llmPicks(), Math.min(size, pool.size()));
        Run run = new Run(q, c, pool, size, k, deadline);
        Outcome outcome = choose(run);
        return finish(run, outcome);
    }

    private Outcome choose(Run run) {
        if (run.expected == 0) {
            return new Outcome(ComposeMode.NO_CANDIDATES, List.of());
        }
        if (!properties.enabled()) {
            return new Outcome(ComposeMode.DISABLED, rules(run, Set.of(), run.expected));
        }
        if (isHard() && !run.deadline.hasAtLeast(properties.minLlm().toMillis())) {
            return new Outcome(ComposeMode.RULES_NO_BUDGET, rules(run, Set.of(), run.expected));
        }
        Outcome llm = askLlm(run);
        if (llm.mode() == ComposeMode.LLM || llm.mode() == ComposeMode.LLM_FILLED) {
            return new Outcome(llm.mode(), fill(run, llm.picks()));
        }
        return new Outcome(llm.mode(), rules(run, Set.of(), run.expected));
    }

    /**
     * Hasta {@code max-attempts} llamadas; el reintento agrega el mensaje {@code retry}, nunca la
     * respuesta mala. Un timeout o una caida terminan sin reintento.
     */
    private Outcome askLlm(Run run) {
        List<ChatMessage> messages = new ArrayList<>(initialMessages(run));
        Map<String, Object> schema = OutputSchema.of(run.pool.size(), run.k);
        ChatOptions options =
                new ChatOptions(properties.temperature(), properties.numCtx(), properties.numPredict(), null);
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            run.attempts = attempt;
            ChatResult result;
            try {
                result = ollama.chat(List.copyOf(messages), schema, options, callTimeout(run));
            } catch (AgentTimeoutException e) {
                return new Outcome(ComposeMode.RULES_TIMEOUT, List.of());
            } catch (AgentUnavailableException e) {
                return new Outcome(ComposeMode.RULES_UNAVAILABLE, List.of());
            }
            List<WrittenPick> picks = validPicks(run, result);
            if (!picks.isEmpty()) {
                return new Outcome(picks.size() >= run.k ? ComposeMode.LLM : ComposeMode.LLM_FILLED, picks);
            }
            if (attempt < properties.maxAttempts() && isHard()
                    && !run.deadline.hasAtLeast(properties.minLlm().toMillis())) {
                return new Outcome(ComposeMode.RULES_NO_BUDGET, List.of());
            }
            messages.add(new ChatMessage("user", PromptRenderer.render(template.retry(), kValue(run))));
        }
        return new Outcome(ComposeMode.RULES_INVALID_OUTPUT, List.of());
    }

    private List<ChatMessage> initialMessages(Run run) {
        Map<String, String> values = PromptRenderer.values(run.query, run.context, run.pool, run.k);
        return List.of(
                new ChatMessage("system", PromptRenderer.render(template.system(), kValue(run))),
                new ChatMessage("user", PromptRenderer.render(template.user(), values)));
    }

    private static Map<String, String> kValue(Run run) {
        return Map.of("k", String.valueOf(run.k));
    }

    /** HARD: {@code min(llm-timeout, restante - 300 ms)}; SOFT: tope fijo de 60 s. */
    private Duration callTimeout(Run run) {
        return isHard() ? run.deadline.callTimeout(properties.llmTimeout().toMillis()) : SOFT_CAP;
    }

    /** Elecciones validas de una respuesta; vacia si se corto ({@code length}) o no trae ninguna. */
    private static List<WrittenPick> validPicks(Run run, ChatResult result) {
        run.add(result);
        if (LENGTH.equals(result.doneReason())) {
            return List.of();
        }
        ValidationResult validation = PickValidator.validate(result.content(), run.pool, run.k);
        run.dropped += validation.dropped();
        run.replaced += validation.replaced();
        return validation.picks();
    }

    /** Las de Mistral primero y luego las reglas hasta completar, sin repetir actividad. */
    private static List<WrittenPick> fill(Run run, List<WrittenPick> llmPicks) {
        Set<Long> chosen = new HashSet<>();
        llmPicks.forEach(p -> chosen.add(p.candidate().activity().getId()));
        List<WrittenPick> list = new ArrayList<>(llmPicks);
        list.addAll(rules(run, chosen, run.expected - llmPicks.size()));
        return list;
    }

    /**
     * Las {@code count} primeras del pool, una por actividad, con la frase del catalogo. El pool ya
     * viene de la mejor a la peor; no se reordena por puntaje porque, si el catalogo completo lo que
     * no junto Qdrant, sus puntajes estan en otra escala.
     */
    private static List<WrittenPick> rules(Run run, Set<Long> exclude, int count) {
        Set<Long> seen = new HashSet<>(exclude);
        return run.pool.stream()
                .filter(c -> seen.add(c.activity().getId()))
                .limit(count)
                .map(c -> new WrittenPick(c, 0, CatalogReasonWriter.reasonOf(c.activity()), ReasonSource.CATALOG))
                .toList();
    }

    private Composition finish(Run run, Outcome outcome) {
        Outcome checked = guarded(run, outcome);
        CompositionTrace trace = new CompositionTrace(
                checked.mode(),
                run.attempts,
                template.version(),
                PROMPT_LEVEL,
                properties.chatModel(),
                properties.timePolicy().name(),
                run.llmMs,
                run.deadline.overBudgetMs(),
                run.promptTokens,
                run.evalTokens,
                run.dropped,
                run.replaced,
                run.doneReason);
        log.info("compose {}", trace.toLogLine());
        return new Composition(checked.picks(), trace);
    }

    /** Si la lista no pasa la guardia, sale la de reglas (que tambien se revisa) y queda un aviso. */
    private static Outcome guarded(Run run, Outcome outcome) {
        try {
            FinalListGuard.check(outcome.picks(), run.pool, run.expected);
            return outcome;
        } catch (GuardException e) {
            log.warn("[t06b] La lista no paso la guardia final ({}); sale por reglas", e.getMessage());
        }
        List<WrittenPick> byRules = rules(run, Set.of(), run.expected);
        try {
            FinalListGuard.check(byRules, run.pool, run.expected);
        } catch (GuardException e) {
            log.warn("[t06b] La lista por reglas tampoco paso la guardia ({}); se guarda igual", e.getMessage());
        }
        return new Outcome(ComposeMode.RULES_INVALID_OUTPUT, byRules);
    }

    private boolean isHard() {
        return properties.timePolicy() == TimePolicy.HARD;
    }

    private static int distinctActivities(List<Candidate> pool) {
        return (int) pool.stream().map(c -> c.activity().getId()).distinct().count();
    }
}
