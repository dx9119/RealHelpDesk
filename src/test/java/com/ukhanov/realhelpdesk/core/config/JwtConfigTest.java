package com.ukhanov.realhelpdesk.core.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Конфигурация JWT: JwtConfig")
class JwtConfigTest {

    /** 32 байта в Base64 — минимум для подписи HS256. */
    private static final String VALID_SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("Из валидных настроек собирается конфиг с ключом и адресами")
    void buildsConfigFromValidProperties() {
        JwtConfig config = new JwtConfig(validProperties());

        assertThat(config.getJwtKey().getEncoded()).hasSize(32);
        assertThat(config.getIssuer()).isEqualTo("https://example.com");
        assertThat(config.getAudience()).containsExactly("https://example.com");
        assertThat(config.getAccessTokenExp()).isEqualTo(60);
        assertThat(config.getRefreshExpiration()).isEqualTo(43200);
    }

    @Test
    @DisplayName("Пустой секрет — ошибка на старте с именем ключа")
    void blankSecretFails() {
        JwtProperties properties = validProperties();
        properties.setSecretForGenJwt(" ");

        assertThatThrownBy(() -> new JwtConfig(properties)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.secret-for-gen-jwt");
    }

    @Test
    @DisplayName("Секрет не в Base64 — ошибка с подсказкой про HS256")
    void secretNotBase64Fails() {
        JwtProperties properties = validProperties();
        properties.setSecretForGenJwt("не-base64!");

        assertThatThrownBy(() -> new JwtConfig(properties)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Base64");
    }

    @Test
    @DisplayName("Пустой issuer — ошибка на старте")
    void blankIssuerFails() {
        JwtProperties properties = validProperties();
        properties.setIssuer("");

        assertThatThrownBy(() -> new JwtConfig(properties)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("jwt.issuer");
    }

    @Test
    @DisplayName("Пустая аудитория — ошибка на старте")
    void emptyAudienceFails() {
        JwtProperties properties = validProperties();
        properties.setAudience(Set.of());

        assertThatThrownBy(() -> new JwtConfig(properties)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.audience");
    }

    @Test
    @DisplayName("Не положительный срок жизни — ошибка на старте")
    void nonPositiveExpirationFails() {
        JwtProperties noAccess = validProperties();
        noAccess.setAccessTokenExpiration(0);
        assertThatThrownBy(() -> new JwtConfig(noAccess)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.access-token-expiration");

        JwtProperties noRefresh = validProperties();
        noRefresh.setRefreshTokenExpiration(-1);
        assertThatThrownBy(() -> new JwtConfig(noRefresh)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.refresh-token-expiration");
    }

    @Test
    @DisplayName("Настройки без обязательных значений — ошибка, а не тихий null")
    void unboundPropertiesFail() {
        assertThatThrownBy(() -> new JwtConfig(new JwtProperties())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.secret-for-gen-jwt");
    }

    private JwtProperties validProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecretForGenJwt(VALID_SECRET);
        properties.setIssuer("https://example.com");
        properties.setAudience(Set.of("https://example.com"));
        properties.setAccessTokenExpiration(60);
        properties.setRefreshTokenExpiration(43200);
        return properties;
    }
}
