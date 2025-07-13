package com.ukhanov.realhelpdesk.core.security.auth.tokens.model;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "jwt_tokens")
public class RefreshTokenModel implements TokenBearer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(unique = false, nullable = false, length = 1000)
    private String tokenRefresh;

    @Enumerated(EnumType.STRING)
    private TokenStatus status = TokenStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserModel user; // Пользователь, которому принадлежит токен

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void setCreatedAt() {
        this.createdAt = Instant.now();
    }

    @Override
    public String getToken() {
        return tokenRefresh;
    }

    public boolean equalsByToken(String token) {
        return Objects.equals(this.tokenRefresh, token);
    }

    public UUID getUuid() {
        return uuid;
    }
    public void setToken(String tokenRefresh) {
        this.tokenRefresh = tokenRefresh;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public UserModel getUser() {
        return user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setUser(UserModel user) {
        this.user = user;
    }

    public TokenStatus getStatus() {
        return status;
    }

    public void setStatus(TokenStatus status) {
        this.status = status;
    }


}
