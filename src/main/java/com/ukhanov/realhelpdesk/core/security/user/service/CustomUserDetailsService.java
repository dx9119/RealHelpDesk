package com.ukhanov.realhelpdesk.core.security.user.service;

import com.ukhanov.realhelpdesk.core.security.user.SecurityUser;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserDomainService userDomainService;

    public CustomUserDetailsService(UserDomainService userDomainService) {
        this.userDomainService = userDomainService;
    }

    @Override
    public SecurityUser loadUserByUsername(String email) throws UsernameNotFoundException {
        UserModel user = userDomainService.getUserByEmail(email);
        return new SecurityUser(user);
    }

    public SecurityUser loadUserById(UUID id) {
        UserModel user = userDomainService.getUserById(id);
        return new SecurityUser(user);
    }
}
