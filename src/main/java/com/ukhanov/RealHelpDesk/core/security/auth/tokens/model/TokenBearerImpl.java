package com.ukhanov.RealHelpDesk.core.security.auth.tokens.model;

public class TokenBearerImpl implements TokenBearer {
    private final String token;

    public TokenBearerImpl(String token) {
        this.token = token;
    }

    @Override
    public String getToken() {
        return token;
    }
}
