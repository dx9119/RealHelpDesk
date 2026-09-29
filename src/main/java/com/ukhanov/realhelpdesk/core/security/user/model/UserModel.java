package com.ukhanov.realhelpdesk.core.security.user.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;

@Entity
@Table(name = "users")
public class UserModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long externalId = 0L; // ID пользователя от внешних интеграций (телеграмм бот, например).

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = true)
    private String middleName; // Отчество пользователя

    @Column(nullable = true, length = 5000)
    String additionalInfo;

    @Column(unique = true, nullable = false)
    private String email;

    private Long verifyEmailToken;

    private Long recoveryPasswdToken;

    private boolean isEmailVerified = false;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private UserRole userRole = UserRole.ROLE_USER;

    @Enumerated(EnumType.STRING)
    private UserStatus userStatus = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    private UserPlatformSource userPlatformSource = UserPlatformSource.LOCAL;

    // Версия access-токенов: инкремент отзывает все ранее выданные access-токены
    @Column(nullable = false)
    private int tokenVersion = 0;

    // Связь с JWT Refresh-токенами
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<RefreshTokenModel> jwtTokenRefresh = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Лимиты
    private Integer portalCuntLimit = 2;
    private Integer portalSharedUsersCountLimit = 2;

    @PrePersist
    public void setCreatedAt() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    @Version
    private Integer version;

    @Override
    public String toString() {
        return "UserModel{" + "createdAt=" + createdAt + ", userRole=" + userRole + ", id=" + id + ", firstName='" + firstName + '\''
                + ", lastName='" + lastName + '\'' + ", middleName='" + middleName + '\'' + ", email='" + email + '\'' + ", userStatus="
                + userStatus + '}';
    }

    public Long getId() {
        return id;
    }

    public boolean isEmailVerified() {
        return isEmailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        isEmailVerified = emailVerified;
    }

    public Long getVerifyEmailToken() {
        return verifyEmailToken;
    }

    public Long getRecoveryPasswdToken() {
        return recoveryPasswdToken;
    }

    public void setRecoveryPasswdToken(Long recoveryPasswdToken) {
        this.recoveryPasswdToken = recoveryPasswdToken;
    }

    public void setVerifyEmailToken(Long verifyEmailToken) {
        this.verifyEmailToken = verifyEmailToken;
    }

    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    public UserPlatformSource getUserExternalSource() {
        return userPlatformSource;
    }

    public void setUserExternalSource(UserPlatformSource userPlatformSource) {
        this.userPlatformSource = userPlatformSource;
    }

    public void setId(Long id) {
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
        return additionalInfo;
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

    public String getFullName() {
        return this.firstName + " " + this.lastName + " " + this.middleName;
    }

    public UserStatus getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(int tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    // Отзывает все ранее выданные access-токены пользователя
    public void incrementTokenVersion() {
        this.tokenVersion++;
    }

    public Long getExternalId() {
        return externalId;
    }

    public void setExternalId(Long externalId) {
        this.externalId = externalId;
    }

    public Integer getPortalCuntLimit() {
        return portalCuntLimit;
    }

    public void setPortalCuntLimit(Integer portalCuntLimit) {
        this.portalCuntLimit = portalCuntLimit;
    }

    public Integer getPortalSharedUsersCountLimit() {
        return portalSharedUsersCountLimit;
    }

    public void setPortalSharedUsersCountLimit(Integer portalSharedUsersCountLimit) {
        this.portalSharedUsersCountLimit = portalSharedUsersCountLimit;
    }
}
