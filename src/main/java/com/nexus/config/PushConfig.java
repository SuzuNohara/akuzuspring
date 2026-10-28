package com.nexus.config;

import com.nexus.integration.expo.ExpoPushClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

/**
 * Envio de push fuera de la solicitud: un Expo lento o caido nunca retrasa aprobar un evento ni
 * vincularse (R3). Si la cola se llena se descarta el envio; el aviso ya esta en la campanita (R4).
 */
@Slf4j
@Configuration
@EnableAsync
public class PushConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final int THREADS = 2;
    private static final int QUEUE = 100;

    @Bean
    public ExpoPushClient expoPushClient(PushProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) properties.timeout().toMillis());
        return new ExpoPushClient(RestClient.builder().requestFactory(factory), properties);
    }

    @Bean("pushExecutor")
    public ThreadPoolTaskExecutor pushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(THREADS);
        executor.setMaxPoolSize(THREADS);
        executor.setQueueCapacity(QUEUE);
        executor.setThreadNamePrefix("push-");
        executor.setRejectedExecutionHandler((task, pool) -> discard());
        return executor;
    }

    private static void discard() {
        log.warn("Cola de push llena: se descarta un envio (el aviso sigue en la campanita)");
    }
}
