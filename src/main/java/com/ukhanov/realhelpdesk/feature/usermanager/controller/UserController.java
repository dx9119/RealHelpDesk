package com.ukhanov.realhelpdesk.feature.usermanager.controller;

import java.io.UnsupportedEncodingException;

import jakarta.mail.MessagingException;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.ratelimit.annotation.RateLimit;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.NewPasswdRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.RecoveryRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoResponse;
import com.ukhanov.realhelpdesk.feature.usermanager.service.UserManageService;

@RestController
@RequestMapping("/api/v1/users")
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

    @PutMapping("/profile")
    public ResponseEntity<UserInfoResponse> updateUserInfo(@Valid @RequestBody UserInfoRequest request) {

        UserInfoResponse response = userManageService.updateUserInfo(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/password-resets")
    @RateLimit(key = "password-reset-request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody RecoveryRequest request)
            throws MessagingException, UnsupportedEncodingException {
        userManageService.sendResetLink(request);

        return ResponseEntity.accepted().build();
    }

    @PutMapping("/password-resets/{code}")
    @RateLimit(key = "password-reset-confirm")
    public ResponseEntity<Void> resetPassword(@PathVariable Long code, @Valid @RequestBody NewPasswdRequest request) throws TokenException {
        userManageService.setNewPasswd(code, request);

        return ResponseEntity.noContent().build();
    }
}
