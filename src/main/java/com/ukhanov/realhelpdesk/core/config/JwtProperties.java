package com.ukhanov.realhelpdesk.core.config;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки JWT из конфигурации: подпись, адреса, сроки жизни и cookie. Только биндинг ключей {@code jwt.*} — валидация значений и
 * вычисление ключа подписи живут в {@link JwtConfig}, их легко тестировать, подставляя готовый экземпляр.
 *
 * <p>
 * Схема ключей: {@code application-domains.properties} (issuer/audience) и {@code application.properties} (секрет, сроки, cookie).
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** Base64-секрет подписи HS256: минимум 32 байта. */
    private String secretForGenJwt;

    /** Домен выпуска токенов: пишется в claim {@code iss} и сверяется при валидации. */
    private String issuer;

    /** Домены-аудитории токенов: claim {@code aud}, список через запятую. */
    private Set<String> audience = new LinkedHashSet<>();

    /** Срок жизни access-токена в минутах. */
    private Integer accessTokenExpiration;

    /** Срок жизни refresh-токена в минутах. */
    private Integer refreshTokenExpiration;

    /**
     * Настройки auth-cookie. Ключи вложены ({@code jwt.cookie.same-site}), поэтому и класс вложен: плоское поле {@code cookieSameSite} из
     * двухэлементного ключа не биндится — см. JwtPropertiesTest.
     */
    private Cookie cookie = new Cookie();

    /** Cookie auth-токенов. */
    public static class Cookie {

        /**
         * SameSite для auth-cookie: каким элементом {@link SameSite} заполнится поле, решает биндинг по ключу {@code jwt.cookie.same-site}
         * — список допустимых значений есть и в enum, и в комментариях у ключа в properties.
         */
        private SameSite sameSite = SameSite.NONE;

        public SameSite getSameSite() {
            return sameSite;
        }

        public void setSameSite(SameSite sameSite) {
            this.sameSite = sameSite;
        }
    }

    public String getSecretForGenJwt() {
        return secretForGenJwt;
    }

    public void setSecretForGenJwt(String secretForGenJwt) {
        this.secretForGenJwt = secretForGenJwt;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Set<String> getAudience() {
        return audience;
    }

    public void setAudience(Set<String> audience) {
        this.audience = audience;
    }

    public Integer getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public void setAccessTokenExpiration(Integer accessTokenExpiration) {
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public Integer getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public void setRefreshTokenExpiration(Integer refreshTokenExpiration) {
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public Cookie getCookie() {
        return cookie;
    }

    public void setCookie(Cookie cookie) {
        this.cookie = cookie;
    }
}
