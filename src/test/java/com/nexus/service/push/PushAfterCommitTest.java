package com.nexus.service.push;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.nexus.config.PushProperties;
import com.nexus.entity.User;
import com.nexus.integration.expo.ExpoPushClient;
import com.nexus.integration.expo.ExpoPushClient.PushResult;
import com.nexus.repository.UserRepository;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La push sale solo si la transaccion del aviso se confirma (R2), y sin transaccion tambien (R5).
 *
 * <p>Usa el {@link PushDispatcher} real con su {@code @TransactionalEventListener} y {@code @Async},
 * un administrador de transacciones sin base de datos y {@code pushExecutor} sincrono: esta laptop no
 * tiene Docker para los *IT, y la regla que importa es la del listener, no la de MySQL.
 */
@SpringJUnitConfig(PushAfterCommitTest.Config.class)
class PushAfterCommitTest {

    private static final long USER_ID = 4L;
    private static final PushRequested EVENT = new PushRequested(
            USER_ID, "EVENT_PENDING", null, "Evento por aprobar", "Sam quiere agregar \"Cena\".", Map.of());

    @Configuration
    @EnableAsync
    @EnableTransactionManagement
    static class Config {
        @Bean
        ExpoPushClient expoPushClient() {
            return mock(ExpoPushClient.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean("pushExecutor")
        SyncTaskExecutor pushExecutor() {
            return new SyncTaskExecutor();
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoDatabaseTransactionManager();
        }

        @Bean
        PushDispatcher pushDispatcher(ExpoPushClient client, UserRepository users) {
            return new PushDispatcher(
                    new PushProperties(true, URI.create("http://expo.test"), Duration.ofSeconds(5)), client, users);
        }
    }

    /** Lleva las sincronizaciones de la transaccion (commit / rollback) sin conexion real. */
    static class NoDatabaseTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }

    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ExpoPushClient client;
    @Autowired private UserRepository users;

    @BeforeEach
    void setUp() {
        reset(client, users);
        given(users.findActiveById(USER_ID))
                .willReturn(Optional.of(User.builder().id(USER_ID).fcmToken("ExponentPushToken[abc]").build()));
        given(client.send(any())).willReturn(PushResult.SENT);
    }

    @Test
    @DisplayName("should send the push once after the transaction commits")
    void shouldSendThePushOnceAfterTheTransactionCommits() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.publishEvent(EVENT);
            verify(client, never()).send(any());
        });

        verify(client).send(any());
    }

    @Test
    @DisplayName("should not send the push when the transaction rolls back")
    void shouldNotSendThePushWhenTheTransactionRollsBack() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    publisher.publishEvent(EVENT);
                    throw new IllegalStateException("fallo al guardar");
                }))
                .isInstanceOf(IllegalStateException.class);

        verify(client, never()).send(any());
    }

    @Test
    @DisplayName("should send the push right away when there is no transaction")
    void shouldSendThePushRightAwayWhenThereIsNoTransaction() {
        publisher.publishEvent(EVENT);

        verify(client).send(any());
    }
}
