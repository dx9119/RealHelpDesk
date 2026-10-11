package com.ukhanov.realhelpdesk.core.config;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Prod-preflight: старт рабочего контура валится на отладочной конфигурации")
class ProdPreflightValidatorTest {

    @TempDir
    Path tempDir;

    private MockEnvironment env;

    @BeforeEach
    void setUp() {
        // Безопасная база: каждый тест подменяет один ключ
        env = new MockEnvironment().withProperty("captcha.enabled", "true").withProperty("jwt.cookie.same-site", "Lax")
                .withProperty("jwt.cookie.domain", "").withProperty("cors.allowed-origins", "https://example.com")
                .withProperty("spring.liquibase.enabled", "true").withProperty("server.ssl.key-alias", "tomcat")
                .withProperty("server.ssl.key-store-password", "changeit").withProperty("server.ssl.key-store-type", "PKCS12");
    }

    @Test
    @DisplayName("Безопасная конфигурация: preflight проходит")
    void secureConfiguration_passes() {
        env.withProperty("server.ssl.key-store", "file:" + keystore("CN=example.com"));

        assertThatCode(() -> new ProdPreflightValidator(env).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Выключенная капча — старт валится")
    void captchaDisabled_fails() {
        env.withProperty("captcha.enabled", "false").withProperty("server.ssl.key-store", "file:" + keystore("CN=example.com"));

        assertThatThrownBy(() -> new ProdPreflightValidator(env).validate()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("captcha.enabled=false");
    }

    @Test
    @DisplayName("SameSite=None без домена cookie — старт валится")
    void sameSiteNoneWithoutDomain_fails() {
        env.withProperty("jwt.cookie.same-site", "None").withProperty("server.ssl.key-store", "file:" + keystore("CN=example.com"));

        assertThatThrownBy(() -> new ProdPreflightValidator(env).validate()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.cookie.same-site=None требует jwt.cookie.domain");
    }

    @Test
    @DisplayName("localhost в CORS — старт валится")
    void localhostInCors_fails() {
        env.withProperty("cors.allowed-origins", "https://localhost.ru,https://example.com").withProperty("server.ssl.key-store",
                "file:" + keystore("CN=example.com"));

        assertThatThrownBy(() -> new ProdPreflightValidator(env).validate()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("localhost");
    }

    @Test
    @DisplayName("Отключённые миграции — старт валится")
    void liquibaseDisabled_fails() {
        env.withProperty("spring.liquibase.enabled", "false").withProperty("server.ssl.key-store", "file:" + keystore("CN=example.com"));

        assertThatThrownBy(() -> new ProdPreflightValidator(env).validate()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.liquibase.enabled=false");
    }

    @Test
    @DisplayName("Самоподписанный keystore CN=localhost — старт валится")
    void localhostKeystore_fails() {
        env.withProperty("server.ssl.key-store", "file:" + keystore("CN=localhost"));

        assertThatThrownBy(() -> new ProdPreflightValidator(env).validate()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CN=localhost");
    }

    @Test
    @DisplayName("Все нарушения перечислены в одном исключении")
    void allViolationsAreCollected() {
        env.withProperty("captcha.enabled", "false").withProperty("jwt.cookie.same-site", "None")
                .withProperty("cors.allowed-origins", "http://localhost:3000").withProperty("spring.liquibase.enabled", "false")
                .withProperty("server.ssl.key-store", "file:" + keystore("CN=localhost"));

        assertThatThrownBy(() -> new ProdPreflightValidator(env).validate()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("captcha.enabled=false").hasMessageContaining("jwt.cookie.same-site=None")
                .hasMessageContaining("localhost:3000").hasMessageContaining("spring.liquibase.enabled=false")
                .hasMessageContaining("CN=localhost");
    }

    /** Самоподписанный PKCS12 тем же способом, что entrypoint контейнера (keytool есть в JDK тестов). */
    private String keystore(String distinguishedName) {
        String file = "ks-" + Math.abs(distinguishedName.hashCode()) + ".p12";
        Path path = tempDir.resolve(file);
        try {
            Process process = new ProcessBuilder("keytool", "-genkeypair", "-alias", "tomcat", "-keyalg", "RSA", "-keysize", "2048",
                    "-storetype", "PKCS12", "-keystore", path.toString(), "-storepass", "changeit", "-keypass", "changeit", "-dname",
                    distinguishedName, "-validity", "30").redirectErrorStream(true).start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new IllegalStateException("keytool не создал keystore (код " + exitCode + "): " + output);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return path.toString();
    }
}
