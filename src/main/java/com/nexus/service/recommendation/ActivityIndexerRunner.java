package com.nexus.service.recommendation;

import com.nexus.config.AgentProperties;
import com.nexus.service.recommendation.agent.OfferIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * [T-06] U5 - Al arrancar, con el agente encendido, deja Qdrant igual al catalogo. Corre en segundo
 * plano para no retrasar el arranque; si Ollama o Qdrant estan caidos solo deja un aviso (R8): las
 * recomendaciones siguen saliendo del catalogo.
 *
 * <p>[t06b-agente-java] Indexa ofertas con {@link OfferIndexer} (solo lo que cambio).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityIndexerRunner implements ApplicationRunner {

    private final OfferIndexer indexer;
    private final AgentProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || !properties.indexOnStartup()) {
            return;
        }
        Thread worker = new Thread(this::indexNow, "t06-indexer");
        worker.setDaemon(true);
        worker.start();
    }

    void indexNow() {
        try {
            indexer.ensureIndexed();
        } catch (RuntimeException e) {
            log.warn("[T-06] No se pudo indexar el catalogo en Qdrant; se usara el catalogo de MySQL: {}", e.getMessage());
        }
    }
}
