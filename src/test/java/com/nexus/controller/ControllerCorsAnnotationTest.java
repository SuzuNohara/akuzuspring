package com.nexus.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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

    /**
     * Raiz de la aplicacion, no el paquete {@code controller}: R5 dice «ningun controlador», y un
     * {@code @RestController} colocado en {@code com.nexus.config} o en cualquier otro paquete
     * seguiria siendo un controlador con politica CORS propia.
     */
    private static final String SCANNED_PACKAGE = "com.nexus";

    private static final int EXPECTED_MINIMUM_CONTROLLERS = 7;

    @Test
    @DisplayName("R5 - ningun controlador declara @CrossOrigin")
    void shouldNotDeclareCrossOriginWhenScanningApplicationPackage() throws ClassNotFoundException {
        Set<BeanDefinition> candidates = scanForControllers();

        List<String> offenders = new ArrayList<>();
        for (BeanDefinition candidate : candidates) {
            Class<?> controller = Class.forName(candidate.getBeanClassName());
            if (controller.isAnnotationPresent(CrossOrigin.class)) {
                offenders.add(controller.getSimpleName());
            }
            for (Method method : handlerCandidatesOf(controller)) {
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

    /**
     * Busca controladores por {@link Controller}, no por {@link RestController}: el segundo esta
     * meta-anotado con el primero, asi que un filtro sobre {@code @Controller} atrapa tambien a los
     * {@code @Controller} + {@code @ResponseBody}, que son controladores REST igual de validos y se
     * escapaban del filtro anterior.
     *
     * @return las definiciones de bean candidatas del paquete de la aplicacion.
     * @implNote O(n) sobre el numero de clases del paquete.
     */
    private static Set<BeanDefinition> scanForControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        return scanner.findCandidateComponents(SCANNED_PACKAGE);
    }

    /**
     * Une los metodos declarados por la clase con los publicos que hereda. {@code
     * getDeclaredMethods()} por si solo ignora un {@code @CrossOrigin} heredado de una clase base,
     * que Spring mapea igual que si estuviera declarado aqui.
     *
     * @param controller la clase de controlador a inspeccionar.
     * @return los metodos que Spring podria mapear, sin repetidos.
     * @implNote O(n) sobre el numero de metodos de la jerarquia.
     */
    private static Set<Method> handlerCandidatesOf(Class<?> controller) {
        Set<Method> methods = new LinkedHashSet<>(List.of(controller.getDeclaredMethods()));
        methods.addAll(List.of(controller.getMethods()));
        return methods;
    }
}
