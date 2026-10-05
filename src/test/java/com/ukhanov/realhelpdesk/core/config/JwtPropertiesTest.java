package com.ukhanov.realhelpdesk.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Конфигурация JWT: JwtProperties")
class JwtPropertiesTest {

    @Test
    @DisplayName("Все ключи jwt.* биндятся: секрет, адреса, сроки и cookie")
    void bindsAllKeys() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("jwt.secret-for-gen-jwt", "c2VjcmV0");
        environment.setProperty("jwt.issuer", "https://example.com");
        environment.setProperty("jwt.audience", "https://example.com,https://back.example.com");
        environment.setProperty("jwt.access-token-expiration", "60");
        environment.setProperty("jwt.refresh-token-expiration", "43200");
        environment.setProperty("jwt.cookie.same-site", "Lax");

        JwtProperties properties = bind(environment);

        assertThat(properties.getSecretForGenJwt()).isEqualTo("c2VjcmV0");
        assertThat(properties.getIssuer()).isEqualTo("https://example.com");
        assertThat(properties.getAudience()).containsExactlyInAnyOrder("https://example.com", "https://back.example.com");
        assertThat(properties.getAccessTokenExpiration()).isEqualTo(60);
        assertThat(properties.getRefreshTokenExpiration()).isEqualTo(43200);
        assertThat(properties.getCookie().getSameSite()).isEqualTo(SameSite.LAX);
    }

    @Test
    @DisplayName("jwt.cookie.same-site: регистр не важен — строка превращается в элемент enum SameSite")
    void sameSiteBindsCaseInsensitively() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("jwt.cookie.same-site", "sTrIcT");

        JwtProperties properties = bind(environment);

        assertThat(properties.getCookie().getSameSite()).isEqualTo(SameSite.STRICT);
    }

    @Test
    @DisplayName("Неизвестное значение same-site — биндинг падает, а не уходит в cookie строкой")
    void unknownSameSiteFailsBinding() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("jwt.cookie.same-site", "Sometimes");

        assertThatThrownBy(() -> bind(environment)).isInstanceOf(BindException.class).hasMessageContaining("jwt.cookie.same-site");
    }

    @Test
    @DisplayName("jwt.cookie.same-site по умолчанию None — кросс-доменный фронт")
    void cookieSameSiteDefaultsToNone() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("jwt.issuer", "https://example.com");

        JwtProperties properties = bind(environment);

        assertThat(properties.getCookie().getSameSite()).isEqualTo(SameSite.NONE);
    }

    @Test
    @DisplayName("Без jwt.* биндинг не срабатывает")
    void emptyEnvironment_isNotBound() {
        BindResult<JwtProperties> result = Binder.get(new MockEnvironment()).bind("jwt", Bindable.of(JwtProperties.class));

        assertThat(result.isBound()).isFalse();
    }

    private JwtProperties bind(MockEnvironment environment) {
        return Binder.get(environment).bind("jwt", Bindable.of(JwtProperties.class)).get();
    }
}
