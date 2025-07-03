package com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto;

public class TokensResponse {

    private String accessToken;
    private String refreshToken;
    private String message;

    public TokensResponse() {
    }

    private TokensResponse(Builder builder) {
        this.accessToken = builder.accessToken;
        this.refreshToken = builder.refreshToken;
        this.message = builder.message;
    }

    public static Builder builder() {
        return new Builder();
    }


    public static class Builder {
        private String accessToken;
        private String refreshToken;
        private String message;

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public TokensResponse build() {
            return new TokensResponse(this);
        }
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getMessage() {
        return message;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "TokensResponse{" +
                "accessToken='" + "***" + '\'' +
                ", refreshToken='" + "***" + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
