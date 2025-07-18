package com.ukhanov.realhelpdesk.feature.usermanager.dto;

import com.ukhanov.realhelpdesk.core.security.user.model.UserPlatformSource;
import com.ukhanov.realhelpdesk.core.security.user.model.UserRole;
import com.ukhanov.realhelpdesk.core.security.user.model.UserStatus;

import java.time.Instant;
import java.util.UUID;

public class UserInfoResponse {

  private UUID id;
  private Long externalId;
  private String firstName;
  private String lastName;
  private String middleName;
  private String additionalInfo;
  private String email;
  private boolean isEmailVerified;
  private UserRole userRole;
  private UserStatus userStatus;
  private UserPlatformSource userPlatformSource;
  private Instant createdAt;

  public UserInfoResponse() {
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public void setExternalId(Long externalId) {
    this.externalId = externalId;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public void setMiddleName(String middleName) {
    this.middleName = middleName;
  }

  public void setAdditionalInfo(String additionalInfo) {
    this.additionalInfo = additionalInfo;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public void setEmailVerified(boolean emailVerified) {
    isEmailVerified = emailVerified;
  }

  public void setUserRole(UserRole userRole) {
    this.userRole = userRole;
  }

  public void setUserStatus(UserStatus userStatus) {
    this.userStatus = userStatus;
  }

  public void setUserPlatformSource(UserPlatformSource userPlatformSource) {
    this.userPlatformSource = userPlatformSource;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }


  public UUID getId() {
    return id;
  }

  public Long getExternalId() {
    return externalId;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getMiddleName() {
    return middleName;
  }

  public String getAdditionalInfo() {
    return additionalInfo;
  }

  public String getEmail() {
    return email;
  }

  public boolean isEmailVerified() {
    return isEmailVerified;
  }

  public UserRole getUserRole() {
    return userRole;
  }

  public UserStatus getUserStatus() {
    return userStatus;
  }

  public UserPlatformSource getUserPlatformSource() {
    return userPlatformSource;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}