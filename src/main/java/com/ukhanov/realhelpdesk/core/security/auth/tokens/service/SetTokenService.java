package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.user.SecurityUser;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import org.springframework.stereotype.Service;

@Service
public class SetTokenService {

    private final SaveTokenService saveTokenService;
    private final GenTokenService genTokenService;

    public SetTokenService(SaveTokenService saveTokenService, GenTokenService genTokenService) {
        this.saveTokenService = saveTokenService;
        this.genTokenService = genTokenService;
    }

    public void addNewRefreshToken(UserModel user) {
        SecurityUser SecUser = new SecurityUser(user);
        RefreshTokenModel refreshToken = genTokenService.generateRefreshJwtToken(SecUser);
        saveTokenService.saveRefreshToken(refreshToken);
    }
}
