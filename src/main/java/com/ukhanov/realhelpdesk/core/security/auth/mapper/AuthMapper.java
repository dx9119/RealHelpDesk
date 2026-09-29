package com.ukhanov.realhelpdesk.core.security.auth.mapper;

import java.security.SecureRandom;
import java.util.Objects;

import com.ukhanov.realhelpdesk.core.security.auth.register.dto.RegisterRequest;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;

public final class AuthMapper {

    private AuthMapper() {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    public static UserModel toEntity(RegisterRequest request, String passwordHash) {
        Objects.requireNonNull(request, "Запрос не должен быть null");
        Objects.requireNonNull(passwordHash, "Хеш пароля не должен быть null");

        UserModel user = new UserModel();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setVerifyEmailToken(RANDOM.nextLong());
        user.setPasswordHash(passwordHash);
        user.setExternalId(request.getExternalId());
        user.setUserExternalSource(request.getUserPlatformSource());

        return user;
    }

}
