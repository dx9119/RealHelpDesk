package com.ukhanov.realhelpdesk.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Конфигурация статики: StaticProperties")
class StaticPropertiesTest {

    @Test
    @DisplayName("static.base-url биндится: домен или ip:port с произвольной схемой")
    void bindsBaseUrl() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("static.base-url", "https://cdn.example.com");

        StaticProperties properties = bind(environment);

        assertThat(properties.getBaseUrl()).isEqualTo("https://cdn.example.com");
    }

    @Test
    @DisplayName("Хвостовой слэш обрезается — к пути не приклеивается двойной")
    void trailingSlashStripped() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("static.base-url", "https://cdn.example.com///");

        StaticProperties properties = bind(environment);

        assertThat(properties.getBaseUrl()).isEqualTo("https://cdn.example.com");
    }

    @Test
    @DisplayName("Пустое значение и пробелы — null: ссылки остаются относительными, как без настройки")
    void blankIsNull() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("static.base-url", "   ");

        StaticProperties properties = bind(environment);

        assertThat(properties.getBaseUrl()).isNull();
    }

    @Test
    @DisplayName("Ключ не задан — base-url null, поведение без настройки не меняется")
    void keyAbsent_defaultsToNull() {
        assertThat(new StaticProperties().getBaseUrl()).isNull();
        assertThat(Binder.get(new MockEnvironment()).bind("static", Bindable.of(StaticProperties.class)).isBound()).isFalse();
    }

    @Test
    @DisplayName("static.cache-control по умолчанию private, no-store — как до появления настройки")
    void cacheControlDefaultsToPrivateNoStore() {
        assertThat(new StaticProperties().getCacheControl()).isEqualTo("private, no-store");
    }

    @Test
    @DisplayName("static.cache-control биндится: значение для CDN уходит в заголовок как есть")
    void bindsCacheControl() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("static.cache-control", "public, max-age=300");

        StaticProperties properties = bind(environment);

        assertThat(properties.getCacheControl()).isEqualTo("public, max-age=300");
    }

    @Test
    @DisplayName("Пустой static.cache-control — закрытый дефолт, а не пустой заголовок в ответе")
    void blankCacheControlFallsBackToDefault() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("static.cache-control", "   ");

        StaticProperties properties = bind(environment);

        assertThat(properties.getCacheControl()).isEqualTo("private, no-store");
    }

    private StaticProperties bind(MockEnvironment environment) {
        return Binder.get(environment).bind("static", Bindable.of(StaticProperties.class)).get();
    }
}
