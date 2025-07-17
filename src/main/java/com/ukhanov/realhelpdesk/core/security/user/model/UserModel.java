package com.ukhanov.realhelpdesk.core.security.user.model;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private Long externalId = 0L; //ID пользователя от внешних интеграций (телеграмм бот, например).

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = true)
    private String middleName; // Отчество пользователя

    @Column(nullable = true, length = 5000)
    String AdditionalInfo;

    @Column(unique = true, nullable = false)
    private String email;

    private UUID verifyEmailToken;

    private boolean isEmailVerified = false;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private UserRole userRole = UserRole.ROLE_USER;

    @Enumerated(EnumType.STRING)
    private UserStatus userStatus = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    private UserPlatformSource userPlatformSource = UserPlatformSource.LOCAL;

    // Связь с JWT Refresh-токенами
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<RefreshTokenModel> jwtTokenRefresh = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void setCreatedAt() {
        this.createdAt = Instant.now();
    }

    @Override
    public String toString() {
        return "UserModel{" +
            "createdAt=" + createdAt +
            ", userRole=" + userRole +
            ", id=" + id +
            ", firstName='" + firstName + '\'' +
            ", lastName='" + lastName + '\'' +
            ", middleName='" + middleName + '\'' +
            ", email='" + email + '\'' +
            ", userStatus=" + userStatus +
            '}';
    }

    public UUID getId() {
        return id;
    }

    public boolean isEmailVerified() {
        return isEmailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        isEmailVerified = emailVerified;
    }

    public UUID getVerifyEmailToken() {
        return verifyEmailToken;
    }

    public void setVerifyEmailToken(UUID verifyEmailToken) {
        this.verifyEmailToken = verifyEmailToken;
    }

    public UserPlatformSource getUserExternalSource() {
        return userPlatformSource;
    }

    public void setUserExternalSource(
        UserPlatformSource userPlatformSource) {
        this.userPlatformSource = userPlatformSource;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getEmail() {
        return email;
    }

    public String getAdditionalInfo() {
        return AdditionalInfo;
    }

    public UserPlatformSource getUserPlatformSource() {
        return userPlatformSource;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Set<RefreshTokenModel> getJwtTokenRefresh() { // Возвращает коллекцию
        return jwtTokenRefresh;
    }

    public void setJwtTokenRefresh(Set<RefreshTokenModel> jwtTokenRefresh) {
        this.jwtTokenRefresh = jwtTokenRefresh;
    }

    public UserRole getUserRole() {
        return userRole;
    }

    public void setUserRole(UserRole userRole) {
        this.userRole = userRole;
    }

    public UserStatus getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
    }

    public Long getExternalId() {
        return externalId;
    }

    public void setExternalId(Long externalId) {
        this.externalId = externalId;
    }
}
