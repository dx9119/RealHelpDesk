package com.ukhanov.realhelpdesk.feature.usermanager.dto;

import java.time.Instant;

import com.ukhanov.realhelpdesk.core.security.user.model.UserPlatformSource;
import com.ukhanov.realhelpdesk.core.security.user.model.UserRole;
import com.ukhanov.realhelpdesk.core.security.user.model.UserStatus;

public record UserInfoResponse(Long id, Long externalId, String firstName, String lastName, String middleName, String additionalInfo,
        String email, boolean emailVerified, UserRole userRole, UserStatus userStatus, UserPlatformSource userPlatformSource,
        Instant createdAt) {
}
