package com.ukhanov.realhelpdesk.core.security.auth.tokens.dto;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;

public class TokenBearerResponse implements TokenBearer {
    String token;

    @Override
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
