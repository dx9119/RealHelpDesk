package com.ukhanov.realhelpdesk.feature.usermanager.controller;

import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoResponse;
import com.ukhanov.realhelpdesk.feature.usermanager.service.UserManageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/")
public class UserController {

  private final UserManageService userManageService;

  public UserController(UserManageService userManageService) {
    this.userManageService = userManageService;
  }

  @GetMapping("/profile")
  public ResponseEntity<UserInfoResponse> getUserInfo() {

    UserInfoResponse response = userManageService.getUserInfo();

    return ResponseEntity.ok(response);
  }

  @PostMapping("/profile")
  public ResponseEntity<UserInfoResponse> UpdateUserInfo(@RequestBody UserInfoRequest request) {

    UserInfoResponse response = userManageService.updateUserInfo(request);

    return ResponseEntity.ok(response);
  }


}
