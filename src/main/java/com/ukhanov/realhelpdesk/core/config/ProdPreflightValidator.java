package com.ukhanov.realhelpdesk.core.config;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Prod-preflight (infraReview.md, п. 3): при профиле {@code prod} старт валится, если рабочий контур собран с отладочными значениями —
 * выключенная капча, {@code SameSite=None} без домена cookie, {@code localhost} в CORS, отключённые миграции или самоподписанный keystore
 * {@code CN=localhost}. Приём повторяет закрепление {@code ddl-auto=validate}: опасная конфигурация не может «тихо» доехать до прода.
 *
 * <p>
 * Проверки читают финальный {@link Environment} (env-файлы + profile-файлы), то есть ловят и забытый override, и подмену в
 * {@code infrastructure/.env}. В debug-профиле компонент не создаётся.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@Profile("prod")
@Component
public class ProdPreflightValidator {

    private static final String LOCALHOST = "localhost";

    private final Environment env;

    @PostConstruct
    void validate() {
        List<String> violations = new ArrayList<>();

        if (!"true".equalsIgnoreCase(env.getProperty("captcha.enabled"))) {
            violations.add("captcha.enabled=false — в рабочем контуре капча должна быть включена");
        }

        String sameSite = env.getProperty("jwt.cookie.same-site", "");
        String cookieDomain = env.getProperty("jwt.cookie.domain", "");
        if ("none".equalsIgnoreCase(sameSite) && cookieDomain.isBlank()) {
            violations.add("jwt.cookie.same-site=None требует jwt.cookie.domain — браузеры отвергают SameSite=None без домена cookie");
        }

        String corsOrigins = env.getProperty("cors.allowed-origins", "");
        if (corsOrigins.toLowerCase().contains("localhost") || corsOrigins.contains("127.0.0.1")) {
            violations.add("cors.allowed-origins содержит localhost/127.0.0.1: «продориgin» в рабочем контуре недопустим — " + corsOrigins);
        }

        if (!"true".equalsIgnoreCase(env.getProperty("spring.liquibase.enabled"))) {
            violations.add("spring.liquibase.enabled=false — миграции схемы в рабочем контуре обязательны");
        }

        checkKeystoreCommonName(violations);

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Prod-preflight: рабочий контур запущен с небезопасной конфигурацией:\n - " + String.join("\n - ", violations));
        }
        logger.info("Prod-preflight пройден: капча включена, миграции обязательны, keystore не самоподписанный CN=localhost");
    }

    /** Самоподписанный keystore {@code CN=localhost} генерирует entrypoint для отладки — в проде он недопустим (infraReview.md, п. 4). */
    private void checkKeystoreCommonName(List<String> violations) {
        String location = env.getProperty("server.ssl.key-store", "");
        String alias = env.getProperty("server.ssl.key-alias", "");
        String password = env.getProperty("server.ssl.key-store-password", "");
        String type = env.getProperty("server.ssl.key-store-type", "PKCS12");

        if (location.isBlank() || alias.isBlank() || password.isBlank()) {
            violations.add("server.ssl.key-store/key-alias/key-store-password не заданы — TLS не настроен");
            return;
        }

        try {
            Resource resource = new DefaultResourceLoader().getResource(location);
            try (InputStream in = resource.getInputStream()) {
                KeyStore keyStore = KeyStore.getInstance(type);
                keyStore.load(in, password.toCharArray());

                if (!keyStore.containsAlias(alias)) {
                    violations.add("в keystore " + location + " нет алиаса '" + alias + "'");
                    return;
                }

                String commonName = commonNameOf(keyStore.getCertificate(alias));
                if (LOCALHOST.equalsIgnoreCase(commonName)) {
                    violations.add(
                            "keystore " + location + " выпущен на CN=localhost (самоподпись entrypoint) — смонтируйте реальный сертификат");
                }
            }
        } catch (Exception e) {
            violations.add("keystore " + location + " не читается: " + e.getMessage());
        }
    }

    private String commonNameOf(java.security.cert.Certificate certificate) throws Exception {
        if (!(certificate instanceof X509Certificate x509)) {
            return null;
        }
        LdapName subject = new LdapName(x509.getSubjectX500Principal().getName());
        for (Rdn rdn : subject.getRdns()) {
            if ("CN".equalsIgnoreCase(rdn.getType())) {
                return String.valueOf(rdn.getValue());
            }
        }
        return null;
    }
}
