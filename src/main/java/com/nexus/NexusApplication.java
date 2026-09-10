package com.nexus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// La auditoria JPA se activa en com.nexus.config.JpaAuditingConfig, no aqui:
// sobre esta clase rompe los cortes de test web. Ver esa clase.
@SpringBootApplication
@ConfigurationPropertiesScan
public class NexusApplication {
    public static void main(String[] args) {
        SpringApplication.run(NexusApplication.class, args);
    }
}
