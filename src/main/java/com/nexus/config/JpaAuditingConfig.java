package com.nexus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activa la auditoria JPA ({@code @CreatedDate} / {@code @LastModifiedDate}).
 *
 * <p>Vive aqui y no en {@code NexusApplication} a proposito: sobre la clase de arranque, la
 * anotacion se aplica tambien a los cortes de test ({@code @WebMvcTest}), que no levantan JPA, y el
 * contexto revienta con {@code JPA metamodel must not be empty}. Como {@code @Configuration}
 * dentro de {@code com.nexus.config}, el escaneo completo la carga igual y el corte web la excluye.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
