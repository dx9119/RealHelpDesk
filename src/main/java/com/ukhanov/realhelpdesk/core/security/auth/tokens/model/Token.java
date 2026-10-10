package com.ukhanov.realhelpdesk.core.security.auth.tokens.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Token implements TokenBearer {
    private String token;

    public Token(String token) {
        this.token = token;
    }
}
