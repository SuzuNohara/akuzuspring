package com.nexus.service.push;

import com.nexus.config.PushProperties;
import com.nexus.entity.User;
import com.nexus.integration.expo.ExpoPushClient;
import com.nexus.integration.expo.ExpoPushClient.PushMessage;
import com.nexus.integration.expo.ExpoPushClient.PushResult;
import com.nexus.repository.UserRepository;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Manda como push cada aviso de la campanita ([push-avisos] U2). Corre despues del commit (R2) y en
 * {@code pushExecutor} (R3); sin transaccion, como en el barrido programado, corre igual (R5).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushDispatcher {

    private final PushProperties properties;
    private final ExpoPushClient client;
    private final UserRepository users;
    private final AtomicBoolean disabledLogged = new AtomicBoolean();

    @Async("pushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(PushRequested event) {
        if (!properties.enabled()) {
            if (disabledLogged.compareAndSet(false, true)) {
                log.info("Push apagado (NEXUS_PUSH_ENABLED=false): los avisos solo quedan en la campanita");
            }
            return;
        }
        String token = users.findActiveById(event.userId()).map(User::getFcmToken).orElse(null);
        if (token == null || token.isBlank()) {
            return;
        }
        PushResult result = client.send(new PushMessage(token, event.title(), event.body(),
                PushRoute.dataFor(event.type(), event.sessionId(), event.extra())));
        if (result == PushResult.TOKEN_INVALID) {
            users.clearTokenIfMatches(event.userId(), token);
            log.info("Token de push vencido para usuario ID: {}; se quito", event.userId());
        }
    }
}
