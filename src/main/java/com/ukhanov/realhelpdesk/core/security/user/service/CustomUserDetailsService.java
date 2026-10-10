package com.ukhanov.realhelpdesk.core.security.user.service;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.security.user.SecurityUser;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserDomainService userDomainService;

    @Override
    public SecurityUser loadUserByUsername(String email) throws UsernameNotFoundException {
        UserModel user = userDomainService.getUserByEmail(email);
        return new SecurityUser(user);
    }

    public SecurityUser loadUserById(Long id) {
        UserModel user = userDomainService.getUserById(id);
        return new SecurityUser(user);
    }
}
