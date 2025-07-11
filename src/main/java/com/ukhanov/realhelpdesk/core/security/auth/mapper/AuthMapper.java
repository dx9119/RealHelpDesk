package com.ukhanov.realhelpdesk.core.security.auth.mapper;


import com.ukhanov.realhelpdesk.core.security.auth.register.dto.RegisterRequest;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;

import java.util.Objects;
import java.util.UUID;

public class AuthMapper {

    public static UserModel toEntity(RegisterRequest request, String passwordHash) {
        Objects.requireNonNull(request, "Request must not be null");
        Objects.requireNonNull(passwordHash, "Password hash must not be null");

        UserModel user = new UserModel();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setVerifyEmailToken(UUID.randomUUID());
        user.setPasswordHash(passwordHash);
        return user;
    }

}
