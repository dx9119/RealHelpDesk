package com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    public RefreshTokenRequest() {
    }

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RefreshTokenRequest that = (RefreshTokenRequest) o;
        return Objects.equals(refreshToken, that.refreshToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(refreshToken);
    }

    @Override
    public String toString() {
        return "RefreshTokenRequest{" +
                "refreshToken='" + refreshToken + '\'' +
                '}';
    }
}
