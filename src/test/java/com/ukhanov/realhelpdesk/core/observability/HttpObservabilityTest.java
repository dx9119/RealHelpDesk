package com.ukhanov.realhelpdesk.core.observability;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

/**
 * HTTP-метрики: uri-шаблон вместо фактического пути, NOT_FOUND для несуществующих маршрутов, тег exception из @ControllerAdvice и тег role.
 * Отдельный контекст не нужен — используется тот же профиль test, что и в ActuatorSecurityTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.profiles.active=test")
@DisplayName("http.server.requests: шаблоны uri, шум, exception и роль")
class HttpObservabilityTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static final String METER_NAME = "http.server.requests";

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("Существующий маршрут даёт uri-шаблон и тег role, а не фактический путь")
    void mappedPathUsesUriTemplate() {
        assertThat(get(serverPort, "/api/v1/health").statusCode()).isEqualTo(200);

        Meter meter = findMeter("uri", "/api/v1/health");
        assertThat(meter).as("метрика с uri=/api/v1/health").isNotNull();
        assertThat(tags(meter)).contains(Tag.of("role", "ANONYMOUS"));
        assertThat(tags(meter)).contains(Tag.of("method", "GET"));
    }

    @Test
    @DisplayName("Несуществующие маршруты схлопываются в uri=NOT_FOUND и не плодят уникальные значения")
    void unknownPathsCollapseToNotFound() {
        int first = get(serverPort, "/api/v1/users/password-resets/unknown/extra-one").statusCode();
        int second = get(serverPort, "/api/v1/users/password-resets/unknown/extra-two").statusCode();

        assertThat(first).as("первый неизвестный маршрут").isEqualTo(404);
        assertThat(second).as("второй неизвестный маршрут").isEqualTo(404);

        long notFoundMeters = metersWith("uri", "NOT_FOUND").size();
        assertThat(notFoundMeters).as("uri=NOT_FOUND — ровно одна серия").isEqualTo(1);
        assertThat(meterRegistry.find(METER_NAME).meters()).allSatisfy(
                meter -> assertThat(tagValue(meter, "uri")).as("uri не должен содержать фактический путь").doesNotContain("extra-"));
    }

    @Test
    @DisplayName("Исключение, перехваченное GlobalExceptionHandler, попадает в тег exception")
    void handledExceptionIsVisibleInTag() {
        // GET на PUT-маршрут восстановления пароля: метод не поддерживается, ответ отдаёт @ControllerAdvice.
        int status = get(serverPort, "/api/v1/users/password-resets/12345").statusCode();

        assertThat(status).isEqualTo(405);
        assertThat(metersWith("exception", "HttpRequestMethodNotSupportedException")).as("тег exception с именем класса, без текста ошибки")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Запросы к actuator — на управляющем порту и в скрейпе — не создают наблюдений")
    void actuatorRequestsAreNotObserved() {
        assertThat(get(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
        assertThat(get(serverPort, "/actuator/health").statusCode()).isIn(401, 403, 404);

        assertThat(meterRegistry.find(METER_NAME).meters()).allSatisfy(
                meter -> assertThat(tagValue(meter, "uri")).as("actuator не должен попадать в метрики").doesNotStartWith("/actuator"));
    }

    @Test
    @DisplayName("Неудачный вход увеличивает auth.attempts{type=login,result=failure} без email в тегах")
    void authAttemptsAreCounted() {
        double before = counterValue("auth.attempts", "type", "login", "result", "failure");

        HttpResponse<String> response = post(serverPort, "/api/v1/auth/login",
                "{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(counterValue("auth.attempts", "type", "login", "result", "failure")).isEqualTo(before + 1);

        meterRegistry.find("auth.attempts").meters()
                .forEach(meter -> assertThat(tags(meter)).extracting(Tag::getKey).doesNotContain("email", "username", "userId"));
    }

    @Test
    @DisplayName("В метриках нет высококардинальных идентификаторов (userId, email, ticketId)")
    void prohibitedTagsAreAbsent() {
        String[] forbidden = {"userId", "user_id", "email", "ticketId", "ticket_id"};

        for (Meter meter : meterRegistry.getMeters()) {
            for (Tag tag : tags(meter)) {
                assertThat(forbidden).as("метрика %s", meter.getId().getName()).doesNotContain(tag.getKey());
            }
        }
    }

    @Test
    @DisplayName("Запросы к одному шаблону с разными ID не растят число уникальных uri")
    void differentIdsDoNotIncreaseUriCardinality() {
        // GET /api/v1/portals/{portalId}/tickets/{ticketId}: разные ID, ответ 401 не важен —
        // важно, что в метрику попадает шаблон маршрута, а не фактический путь. Первая серия прогревает
        // (запись meter'а может чуть отстать от ответа — observation стопается на commit), вторая должна
        // обойтись без новых uri-значений. Реестр может быть общим для @SpringBootTest-классов, поэтому
        // сравниваем множества после серий, а не абсолютные числа.
        requestSeriesWithDifferentIds(1);
        Set<String> afterFirstSeries = uniqueUriValues();

        requestSeriesWithDifferentIds(11);

        assertThat(uniqueUriValues()).as("вторая серия с другими ID не добавляет uri-значений").isEqualTo(afterFirstSeries);
        assertThat(meterRegistry.find(METER_NAME).meters()).allSatisfy(meter -> assertThat(tagValue(meter, "uri"))
                .as("uri не должен содержать фактический portalId/ticketId").doesNotContain("1001", "1002", "1011", "1012"));
    }

    private void requestSeriesWithDifferentIds(int offset) {
        for (int i = 1; i <= 10; i++) {
            int status = get(serverPort, "/api/v1/portals/" + (offset + i) + "/tickets/" + (1000 + offset + i)).statusCode();
            assertThat(status).as("статус запроса %d", offset + i).isIn(401, 403, 404, 200);
        }
    }

    @Test
    @DisplayName("Отключённые deny-метрики (disk, jvm.buffer) не появляются в реестре")
    void deniedMetricsAreAbsent() {
        get(serverPort, "/api/v1/health");

        assertThat(meterRegistry.find("disk.free").meter()).as("disk.free отклонён MeterFilter").isNull();
        assertThat(meterRegistry.find("disk.total").meter()).as("disk.total отклонён MeterFilter").isNull();
        assertThat(meterRegistry.find("jvm.buffer.count").meter()).as("jvm.buffer.count отклонён MeterFilter").isNull();
        assertThat(meterRegistry.find("jvm.buffer.memory.used").meter()).as("jvm.buffer.memory.used отклонён MeterFilter").isNull();
    }

    @Test
    @DisplayName("Пул планировщика измеряется: executor.* с тегом name=taskScheduler")
    void taskSchedulerExecutorIsBound() {
        assertThat(meterRegistry.find("executor.pool.max").tags("name", "taskScheduler").meter())
                .as("Boot автоматически биндит ThreadPoolTaskScheduler как executor.*{name=taskScheduler}").isNotNull();
        assertThat(meterRegistry.find("executor.queue.remaining").tags("name", "taskScheduler").meter())
                .as("очередь планировщика измеряется").isNotNull();
    }

    private Set<String> uniqueUriValues() {
        return meterRegistry.find(METER_NAME).meters().stream()
                .flatMap(meter -> meter.getId().getTags().stream().filter(tag -> tag.getKey().equals("uri"))).map(Tag::getValue)
                .collect(Collectors.toSet());
    }

    private Meter findMeter(String key, String value) {
        return meterRegistry.find(METER_NAME).tags(key, value).meter();
    }

    private Collection<Meter> metersWith(String key, String value) {
        return meterRegistry.find(METER_NAME).tags(key, value).meters();
    }

    private double counterValue(String name, String... tags) {
        Meter meter = meterRegistry.find(name).tags(tags).meter();
        return meter == null ? 0 : ((io.micrometer.core.instrument.Counter) meter).count();
    }

    private static Iterable<Tag> tags(Meter meter) {
        return meter.getId().getTags();
    }

    private static String tagValue(Meter meter, String key) {
        for (Tag tag : meter.getId().getTags()) {
            if (tag.getKey().equals(key)) {
                return tag.getValue();
            }
        }
        return "";
    }

    private HttpResponse<String> get(int port, String path) {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15)).GET().build());
    }

    private HttpResponse<String> post(int port, String path, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header("X-Requested-With", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return send(request);
    }

    private static HttpResponse<String> send(HttpRequest request) {
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Запрос не выполнен: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запрос прерван: " + request.uri(), e);
        }
    }
}
