package com.ukhanov.realhelpdesk.core.config;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;

import com.ukhanov.realhelpdesk.core.observability.HttpServerRequestObservationConvention;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;

/**
 * Защитный слой наблюдаемости (obs.md, шаги 2, 4 и 10): отсекает шум и добавляет тег роли в http.server.requests.
 *
 * <p>
 * В Boot 4 свойства {@code management.observations.http.server.requests.ignore-patterns} больше нет, поэтому исключения задаются
 * {@link ObservationPredicate}: бин подхватывается {@code ObservationRegistryPostProcessor} и гасит наблюдение целиком — ни метрики, ни
 * спана. То же относится и к управляющему порту: пробы health и скрейп Prometheus не должны раздувать список uri.
 * </p>
 */
@Configuration
public class ObservabilityConfiguration {

    private static final String SERVER_REQUESTS = "http.server.requests";

    /**
     * Пути, запросы к которым не несут информации о поведении приложения: actuator (включая пробы и скрейп), Swagger UI и контракты
     * OpenAPI.
     */
    private static final String[] NOISE_PREFIXES = {"/actuator", "/swagger-ui", "/v3/api-docs"};

    @Bean
    ServerRequestObservationConvention serverRequestObservationConvention(
            @Value("${management.observations.http.server.requests.name:http.server.requests}") String observationName) {
        return new HttpServerRequestObservationConvention(observationName);
    }

    @Bean
    ObservationPredicate ignoreNoiseObservationPredicate() {
        return (name, context) -> !SERVER_REQUESTS.equals(name) || !isNoise(context);
    }

    private static boolean isNoise(Observation.Context context) {
        if (!(context instanceof ServerRequestObservationContext serverContext)) {
            return false;
        }
        HttpServletRequest request = serverContext.getCarrier();
        if (request == null) {
            return false;
        }
        String uri = request.getRequestURI();
        for (String prefix : NOISE_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
