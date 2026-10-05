package com.ukhanov.realhelpdesk.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Конфигурация CORS: CorsProperties")
class CorsPropertiesTest {

    @Test
    @DisplayName("cors.allowed-origins биндится списком через запятую")
    void bindsCommaSeparatedOrigins() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("cors.allowed-origins", "https://localhost.ru,https://example.com");

        CorsProperties properties = bind(environment);

        assertThat(properties.getAllowedOrigins()).containsExactly("https://localhost.ru", "https://example.com");
    }

    @Test
    @DisplayName("Лишние пробелы вокруг origin вычищаются — браузер сравнивает origin буквально")
    void trimsWhitespaceAroundOrigins() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("cors.allowed-origins", " https://localhost.ru , https://example.com ");

        CorsProperties properties = bind(environment);

        assertThat(properties.getAllowedOrigins()).containsExactly("https://localhost.ru", "https://example.com");
    }

    @Test
    @DisplayName("Без cors.* биндинг не срабатывает")
    void emptyEnvironment_isNotBound() {
        BindResult<CorsProperties> result = Binder.get(new MockEnvironment()).bind("cors", Bindable.of(CorsProperties.class));

        assertThat(result.isBound()).isFalse();
    }

    private CorsProperties bind(MockEnvironment environment) {
        return Binder.get(environment).bind("cors", Bindable.of(CorsProperties.class)).get();
    }
}
