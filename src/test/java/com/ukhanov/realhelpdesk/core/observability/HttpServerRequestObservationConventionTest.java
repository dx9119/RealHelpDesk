package com.ukhanov.realhelpdesk.core.observability;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.common.KeyValue;

/**
 * Тег role: конечный набор значений, значение по умолчанию — ANONYMOUS, никаких id и email.
 */
@DisplayName("Тег role в http.server.requests")
class HttpServerRequestObservationConventionTest {

    private final HttpServerRequestObservationConvention convention = new HttpServerRequestObservationConvention("http.server.requests");

    @Test
    @DisplayName("Без атрибута роли — ANONYMOUS")
    void anonymousWhenRoleAttributeIsMissing() {
        assertThat(roleOf(new MockHttpServletRequest("GET", "/api/v1/health"))).isEqualTo("ANONYMOUS");
    }

    @Test
    @DisplayName("Роль из атрибута запроса попадает в тег без префикса ROLE_")
    void roleFromRequestAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
        request.setAttribute(HttpServerRequestObservationConvention.ROLE_ATTRIBUTE, "ROLE_ADMIN");

        assertThat(roleOf(request)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Сохраняются стандартные теги исключения, метода, исхода, статуса и uri")
    void defaultKeyValuesAreKept() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");

        List<String> keys = keysOf(request);

        assertThat(keys).contains("exception", "method", "outcome", "role", "status", "uri");
        assertThat(keys).isEqualTo(keys.stream().sorted().toList());
    }

    private String roleOf(MockHttpServletRequest request) {
        return keyValue(request, "role").getValue();
    }

    private KeyValue keyValue(MockHttpServletRequest request, String key) {
        return keysAndValues(request).stream().filter(value -> value.getKey().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("Нет тега " + key));
    }

    private List<String> keysOf(MockHttpServletRequest request) {
        return keysAndValues(request).stream().map(KeyValue::getKey).toList();
    }

    private List<KeyValue> keysAndValues(MockHttpServletRequest request) {
        ServerRequestObservationContext context = new ServerRequestObservationContext(request, new MockHttpServletResponse());
        return convention.getLowCardinalityKeyValues(context).stream().toList();
    }
}
