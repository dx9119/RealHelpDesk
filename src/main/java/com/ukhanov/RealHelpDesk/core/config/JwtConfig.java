package com.ukhanov.RealHelpDesk.core.config;

import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Set;

@Component
public class JwtConfig {

    private static final Logger logger = LoggerFactory.getLogger(JwtConfig.class);

    private Key jwtSecret;
    private String issuer;
    private Set<String> audience;
    private Integer accessTokenExp;
    private Integer refreshExp;

    public JwtConfig() {
        this.issuer = "your-issuer";
        this.audience = Set.of("audience", "audience2");
        this.accessTokenExp = 30;
        this.refreshExp = 1440;
    }

    @PostConstruct
    private void init() {
        reloadSecret();
    }

    public void reloadSecret() {
        String secretEnv = System.getenv("JWT_SECRET");

        if (secretEnv == null || secretEnv.isEmpty()) {
            logger.error("JWT_SECRET environment variable is not set. Application startup aborted.");
            throw new IllegalStateException("JWT_SECRET environment variable is missing.");
        }

        try {
            this.jwtSecret = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretEnv));
            logger.info("JWT secret successfully loaded.");
        } catch (IllegalArgumentException e) {
            logger.error("Invalid JWT_SECRET format. Ensure it's correctly Base64 encoded.", e);
            throw new IllegalStateException("Invalid JWT_SECRET format.");
        }
    }

    public Key getJwtSecret() {
        return jwtSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public Set<String> getAudience() {
        return audience;
    }

    public Integer getAccessTokenExp() {
        return accessTokenExp;
    }

    public Integer getRefreshExpiration() {
        return refreshExp;
    }
}
