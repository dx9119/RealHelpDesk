package com.ukhanov.realhelpdesk.feature.usermanager.service;

import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoResponse;
import com.ukhanov.realhelpdesk.feature.usermanager.mapper.UserMapper;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserManageService {

  private static final Logger logger = LoggerFactory.getLogger(UserManageService.class);

  private final CurrentUserProvider currentUserProvider;
  private final UserDomainService userDomainService;

  public UserManageService(CurrentUserProvider currentUserProvider,
      UserMapper userMapper, UserDomainService userDomainService) {
    this.currentUserProvider = currentUserProvider;
    this.userDomainService = userDomainService;
  }

  public UserInfoResponse getUserInfo() {
    UserModel user = currentUserProvider.getCurrentUserModel();
    return UserMapper.toResponse(user);
  }


  public UserInfoResponse updateUserInfo(UserInfoRequest request) {
    Objects.requireNonNull(request, "Request can't be null");

    UserModel currentUser = currentUserProvider.getCurrentUserModel();

    currentUser.setFirstName(request.getFirstName());
    currentUser.setLastName(request.getLastName());
    currentUser.setMiddleName(request.getMiddleName());
    currentUser.setAdditionalInfo(request.getAdditionalInfo());

    UserModel updatedUser = userDomainService.saveUser(currentUser);

    logger.info("User info updated for userId={}", updatedUser.getId());

    return UserMapper.toResponse(updatedUser);
  }

}
