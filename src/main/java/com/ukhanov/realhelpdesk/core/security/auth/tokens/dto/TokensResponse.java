package com.ukhanov.realhelpdesk.core.security.auth.tokens.dto;

public record TokensResponse(String accessToken, String refreshToken, String message) {

    @Override
    public String toString() {
        return "TokensResponse{" + "accessToken='" + "***" + '\'' + ", refreshToken='" + "***" + '\'' + ", message='" + message + '\''
                + '}';
    }
}
