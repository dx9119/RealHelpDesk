package com.ukhanov.realhelpdesk.core.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Безопасность и доступность actuator на управляющем порту (obs.md, шаг 3).
 *
 * <p>
 * Приложение поднимается с {@code spring.profiles.active=test} (профиль {@code src/test/resources/application-test.properties}):
 * prod-preflight требует боевого keystore и включённых миграций, а контекст тестам нужен без инфраструктуры.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.profiles.active=test")
@DisplayName("Actuator: управляющий порт, Basic-скрейп и закрытие /actuator на основном порту")
class ActuatorSecurityTest {

    private static final String METRICS_USER = "test-metrics";
    private static final String METRICS_PASSWORD = "test-password";

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("health, liveness и readiness отвечают без аутентификации — пробы оркестратора работают")
    void healthProbesAreOpen() {
        assertThat(get(managementPort, "/actuator/health", null).statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health/liveness", null).statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health/readiness", null).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("prometheus без учётных данных — 401: скрейп открыт только сервисной учётке")
    void prometheusRejectsScrapeWithoutCredentials() {
        HttpResponse<String> response = get(managementPort, "/actuator/prometheus", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).doesNotContain("jvm_memory_used_bytes");
    }

    @Test
    @DisplayName("prometheus с Basic-учёткой — 200, метрики JVM и теги application/env на месте")
    void prometheusAcceptsScrapeWithServiceCredentials() {
        HttpResponse<String> response = get(managementPort, "/actuator/prometheus", METRICS_USER + ":" + METRICS_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm_memory_used_bytes");
        assertThat(response.body()).contains("application=\"test-app\"").contains("env=\"test\"");
    }

    @Test
    @DisplayName("Невыгруженные endpoints (env, heapdump) недоступны даже с учёткой скрейпа")
    void unexposedEndpointsAreNotServed() {
        String credentials = METRICS_USER + ":" + METRICS_PASSWORD;

        for (String path : new String[]{"/actuator/env", "/actuator/heapdump", "/actuator/configprops", "/actuator/mappings"}) {
            int status = get(managementPort, path, credentials).statusCode();
            assertThat(status).as("статус %s", path).isIn(401, 403, 404);
        }
    }

    @Test
    @DisplayName("На основном порту actuator не обслуживается: prometheus не отдаёт метрики")
    void applicationPortDoesNotServeActuator() {
        assertThat(get(serverPort, "/actuator/prometheus", null).statusCode()).isIn(401, 403, 404);

        HttpResponse<String> withCredentials = get(serverPort, "/actuator/prometheus", METRICS_USER + ":" + METRICS_PASSWORD);
        assertThat(withCredentials.body()).doesNotContain("jvm_memory_used_bytes");
    }

    @Test
    @DisplayName("Запрос к API попадает в http.server.requests и виден в скрейпе")
    void httpRequestsAreMeasured() {
        HttpResponse<String> health = get(serverPort, "/api/v1/health", null);
        assertThat(health.statusCode()).isEqualTo(200);

        HttpResponse<String> scrape = get(managementPort, "/actuator/prometheus", METRICS_USER + ":" + METRICS_PASSWORD);
        assertThat(scrape.body()).contains("http_server_requests");
        assertThat(meterRegistry.find("http.server.requests").meters()).isNotEmpty();
    }

    private HttpResponse<String> get(int port, String path, String basicCredentials) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15))
                .GET();
        if (basicCredentials != null) {
            String encoded = Base64.getEncoder().encodeToString(basicCredentials.getBytes(StandardCharsets.UTF_8));
            request.header("Authorization", "Basic " + encoded);
        }
        try {
            return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Запрос не выполнен: " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запрос прерван: " + path, e);
        }
    }
}
