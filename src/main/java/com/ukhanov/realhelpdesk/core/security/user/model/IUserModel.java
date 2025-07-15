package com.ukhanov.realhelpdesk.core.security.user.model;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface IUserModel {
  UUID getId();

  Long getExternalId();
  void setExternalId(Long externalId);

  String getFirstName();
  void setFirstName(String firstName);

  String getLastName();
  void setLastName(String lastName);

  String getMiddleName();
  void setMiddleName(String middleName);

  String getEmail();
  void setEmail(String email);

  UUID getVerifyEmailToken();
  void setVerifyEmailToken(UUID verifyEmailToken);

  boolean isEmailVerified();
  void setEmailVerified(boolean emailVerified);

  String getPasswordHash();
  void setPasswordHash(String passwordHash);

  UserRole getUserRole();
  void setUserRole(UserRole userRole);

  UserStatus getUserStatus();
  void setUserStatus(UserStatus userStatus);

  UserPlatformSource getUserExternalSource();
  void setUserExternalSource(UserPlatformSource userPlatformSource);

  Set<RefreshTokenModel> getJwtTokenRefresh();
  void setJwtTokenRefresh(Set<RefreshTokenModel> jwtTokenRefresh);

  /**
   * Задается через @PrePersist
   * this.createdAt = Instant.now()
   */
  Instant getCreatedAt();
}

