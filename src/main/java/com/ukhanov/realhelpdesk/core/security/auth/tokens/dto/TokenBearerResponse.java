package com.ukhanov.realhelpdesk.core.security.auth.tokens.dto;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;

public record TokenBearerResponse(String token) implements TokenBearer {

    @Override
    public String getToken() {
        return token;
    }
}
