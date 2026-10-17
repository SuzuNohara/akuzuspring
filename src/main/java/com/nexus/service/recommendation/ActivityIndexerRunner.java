package com.nexus.service.recommendation;

import com.nexus.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * [T-06] U5 - Al arrancar, con el agente encendido, revisa que Qdrant tenga el catalogo y lo
 * reindexa si no. Corre en segundo plano para no retrasar el arranque; si Ollama o Qdrant estan
 * caidos solo deja un aviso (R8): las recomendaciones siguen saliendo del catalogo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityIndexerRunner implements ApplicationRunner {

    private final ActivityIndexer indexer;
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
            int indexed = indexer.ensureIndexed();
            if (indexed == 0) {
                log.info("[T-06] Qdrant ya tiene el catalogo al dia");
            }
        } catch (RuntimeException e) {
            log.warn("[T-06] No se pudo indexar el catalogo en Qdrant; se usara el catalogo de MySQL: {}", e.getMessage());
        }
    }
}
