package com.nexus.config;

import com.nexus.integration.weather.OpenMeteoClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Cliente del clima con tiempos acotados: un pronostico lento nunca retrasa de mas la solicitud. */
@Configuration
public class WeatherClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    @Bean
    public OpenMeteoClient openMeteoClient(WeatherProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) properties.timeout().toMillis());
        return new OpenMeteoClient(RestClient.builder().requestFactory(factory), properties);
    }
}
