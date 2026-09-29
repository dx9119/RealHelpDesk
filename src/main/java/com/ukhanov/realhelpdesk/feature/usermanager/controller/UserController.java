package com.ukhanov.realhelpdesk.feature.usermanager.controller;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import jakarta.mail.MessagingException;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.ratelimit.annotation.RateLimit;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.NewPasswdRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.RecoveryRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoResponse;
import com.ukhanov.realhelpdesk.feature.usermanager.service.UserManageService;

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
    public ResponseEntity<UserInfoResponse> updateUserInfo(@Valid @RequestBody UserInfoRequest request) {

        UserInfoResponse response = userManageService.updateUserInfo(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/passwd-reset/request")
    @RateLimit(requests = 3, windowSeconds = 600)
    public ResponseEntity<Map<String, String>> passwdResetLink(@Valid @RequestBody RecoveryRequest request)
            throws MessagingException, UnsupportedEncodingException {
        userManageService.sendResetLink(request);

        Map<String, String> responce = Map.of("Статус", "Успех");
        return ResponseEntity.ok(responce);
    }

    @PostMapping("/passwd-reset/confirm")
    @RateLimit(requests = 5, windowSeconds = 600)
    public ResponseEntity<Map<String, String>> newPasswdSet(@Valid @RequestParam Long code, @RequestBody NewPasswdRequest request)
            throws TokenException {
        userManageService.setNewPasswd(code, request);
        Map<String, String> responce = Map.of("Статус", "Успех");
        return ResponseEntity.ok(responce);
    }
}
