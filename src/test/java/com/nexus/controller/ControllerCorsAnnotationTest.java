package com.nexus.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La politica CORS tiene una sola fuente de verdad: el bean {@code CorsConfigurationSource}.
 *
 * <p>Cubre R5 de nexus-AUTH-06: ningun controlador puede declarar su propia politica por
 * anotacion, ni a nivel de clase ni de metodo.
 *
 * @implNote O(n) sobre el numero de controladores del paquete.
 */
class ControllerCorsAnnotationTest {

    private static final String CONTROLLER_PACKAGE = "com.nexus.controller";
    private static final int EXPECTED_MINIMUM_CONTROLLERS = 7;

    @Test
    @DisplayName("R5 - ningun @RestController declara @CrossOrigin")
    void shouldNotDeclareCrossOriginWhenScanningControllerPackage() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<BeanDefinition> candidates = scanner.findCandidateComponents(CONTROLLER_PACKAGE);

        List<String> offenders = new ArrayList<>();
        for (BeanDefinition candidate : candidates) {
            Class<?> controller = Class.forName(candidate.getBeanClassName());
            if (controller.isAnnotationPresent(CrossOrigin.class)) {
                offenders.add(controller.getSimpleName());
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isAnnotationPresent(CrossOrigin.class)) {
                    offenders.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(candidates)
                .as("guarda contra un escaneo vacio que pasaria en falso")
                .hasSizeGreaterThanOrEqualTo(EXPECTED_MINIMUM_CONTROLLERS);
        assertThat(offenders)
                .as("la politica CORS solo vive en CorsConfigurationSource")
                .isEmpty();
    }
}
