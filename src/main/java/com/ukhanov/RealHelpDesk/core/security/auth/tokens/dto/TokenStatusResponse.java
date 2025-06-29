package com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto;

import com.ukhanov.RealHelpDesk.core.security.auth.tokens.model.TokenStatus;

import java.time.Instant;

public class TokenStatusResponse {

    private TokenStatus tokenStatus;
    private Instant createdAt;

    private TokenStatusResponse(Builder builder) {
        this.tokenStatus = builder.tokenStatus;
        this.createdAt = builder.createdAt;
    }

    public TokenStatus getTokenStatus() {
        return tokenStatus;
    }

    public void setTokenStatus(TokenStatus tokenStatus) {
        this.tokenStatus = tokenStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TokenStatus tokenStatus;
        private Instant createdAt;

        public Builder tokenStatus(TokenStatus tokenStatus) {
            this.tokenStatus = tokenStatus;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public TokenStatusResponse build() {
            return new TokenStatusResponse(this);
        }
    }
}
