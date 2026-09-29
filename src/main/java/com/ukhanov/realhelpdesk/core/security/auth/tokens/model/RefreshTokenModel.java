package com.ukhanov.realhelpdesk.core.security.auth.tokens.model;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jwt_tokens")
public class RefreshTokenModel implements TokenBearer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    // В БД хранится только SHA-256 хеш; сырой токен живёт только в памяти (для cookie)
    @Column(unique = true, nullable = false, length = 64)
    private String tokenRefresh;

    @Transient
    private String rawToken;

    @Enumerated(EnumType.STRING)
    private TokenStatus status = TokenStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserModel user; // Пользователь, которому принадлежит токен

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void setCreatedAt() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    @Override
    public String getToken() {
        return tokenRefresh;
    }

    public UUID getUuid() {
        return uuid;
    }
    public void setToken(String tokenRefresh) {
        this.tokenRefresh = tokenRefresh;
    }

    public String getRawToken() {
        return rawToken;
    }

    public void setRawToken(String rawToken) {
        this.rawToken = rawToken;
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
