package com.nexus.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.service.MoodAverage;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guarda estructural de RN-28 (nexus-EMO-01, R12): ningún {@code @RestController} de {@code
 * com.nexus.controller} puede devolver ni recibir {@link MoodAverage}, ni siquiera envuelto en
 * {@code ResponseEntity}/{@code List} o genéricos. El promedio es privado y solo se consume en Java.
 */
class NoMoodAverageEndpointTest {

    @Test
    @DisplayName("should not expose MoodAverage from any controller")
    void shouldNotExposeMoodAverageFromAnyController() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        for (BeanDefinition bd : provider.findCandidateComponents("com.nexus.controller")) {
            Class<?> controller = Class.forName(bd.getBeanClassName());
            for (Method method : controller.getDeclaredMethods()) {
                assertThat(mentionsMoodAverage(method.getGenericReturnType()))
                        .as("%s#%s devuelve MoodAverage", controller.getSimpleName(), method.getName())
                        .isFalse();
                for (Type param : method.getGenericParameterTypes()) {
                    assertThat(mentionsMoodAverage(param))
                            .as("%s#%s recibe MoodAverage", controller.getSimpleName(), method.getName())
                            .isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("detector flags MoodAverage even wrapped in generics (meta-test)")
    void detectorFlagsMoodAverageEvenWrappedInGenerics() throws NoSuchMethodException {
        assertThat(mentionsMoodAverage(Leak.class.getMethod("raw").getGenericReturnType())).isTrue();
        assertThat(mentionsMoodAverage(Leak.class.getMethod("wrapped").getGenericReturnType()))
                .isTrue();
        assertThat(mentionsMoodAverage(Leak.class.getMethod("listed").getGenericReturnType()))
                .isTrue();
        assertThat(
                        mentionsMoodAverage(
                                Leak.class.getMethod("param", MoodAverage.class)
                                        .getGenericParameterTypes()[0]))
                .isTrue();
        assertThat(mentionsMoodAverage(Leak.class.getMethod("safe").getGenericReturnType())).isFalse();
    }

    private boolean mentionsMoodAverage(Type type) {
        if (type instanceof Class<?> c) {
            return MoodAverage.class.equals(c);
        }
        if (type instanceof ParameterizedType pt) {
            if (mentionsMoodAverage(pt.getRawType())) {
                return true;
            }
            for (Type arg : pt.getActualTypeArguments()) {
                if (mentionsMoodAverage(arg)) {
                    return true;
                }
            }
        }
        if (type instanceof GenericArrayType gat) {
            return mentionsMoodAverage(gat.getGenericComponentType());
        }
        if (type instanceof WildcardType wt) {
            for (Type bound : wt.getUpperBounds()) {
                if (mentionsMoodAverage(bound)) {
                    return true;
                }
            }
            for (Type bound : wt.getLowerBounds()) {
                if (mentionsMoodAverage(bound)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Clase de prueba del detector: no es un controlador, solo prueba el escáner. */
    @SuppressWarnings("unused")
    static class Leak {
        public MoodAverage raw() {
            return null;
        }

        public ResponseEntity<MoodAverage> wrapped() {
            return null;
        }

        public List<MoodAverage> listed() {
            return null;
        }

        public void param(MoodAverage average) {}

        public String safe() {
            return null;
        }
    }
}
