package com.ukhanov.realhelpdesk.feature.usermanager.mapper;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoResponse;

@Component
public class UserMapper {

    public static UserInfoResponse toResponse(UserModel userModel) {
        Objects.requireNonNull(userModel, "userModel не должен быть пустым");

        UserInfoResponse response = new UserInfoResponse();

        response.setId(userModel.getId());
        response.setExternalId(userModel.getExternalId());
        response.setFirstName(userModel.getFirstName());
        response.setLastName(userModel.getLastName());
        response.setMiddleName(userModel.getMiddleName());
        response.setAdditionalInfo(userModel.getAdditionalInfo());
        response.setEmail(userModel.getEmail());
        response.setEmailVerified(userModel.isEmailVerified());
        response.setUserRole(userModel.getUserRole());
        response.setUserStatus(userModel.getUserStatus());
        response.setUserPlatformSource(userModel.getUserPlatformSource());
        response.setCreatedAt(userModel.getCreatedAt());
        return response;
    }

    public static UserModel toModel(UserInfoRequest request) {
        Objects.requireNonNull(request, "request не должен быть пустым");

        UserModel userModel = new UserModel();

        userModel.setFirstName(request.getFirstName());
        userModel.setLastName(request.getLastName());
        userModel.setMiddleName(request.getMiddleName());
        userModel.setAdditionalInfo(request.getAdditionalInfo());

        return userModel;
    }

}
