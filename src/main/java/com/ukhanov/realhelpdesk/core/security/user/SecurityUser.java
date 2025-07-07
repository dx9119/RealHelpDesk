package com.ukhanov.realhelpdesk.core.security.user;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.model.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;


public class SecurityUser implements UserDetails {
    private final UserModel user;

    public SecurityUser(UserModel user) {
        this.user = user;
    }

    public UserModel getOriginalUser() {
        return this.user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> userRoles = new HashSet<>();
        String userRole = String.valueOf(user.getUserRole());
        userRoles.add(new SimpleGrantedAuthority(userRole));

        return userRoles;
    }

    public String getId() {
        return user.getId().toString();
    }

    public String getRule() {
        return String.valueOf(user.getUserRole());
    }

    public Optional<String> getLatestJwtRefreshToken() {
        return this.user
                .getJwtTokenRefresh() // Получаем список всех refresh-токенов, связанных с пользователем
                .stream() // список в потоки
                .max(Comparator.comparing(RefreshTokenModel::getCreatedAt)) // Сравниваем refresh-токены по дате создания
                .map(RefreshTokenModel::getToken); // Преобразуем найденный объект в строковое значение токена
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getUserStatus() == UserStatus.ACTIVE;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // Здесь должна быть логика проверки, не истек ли срок действия учетных данных пользователя
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getUserStatus() == UserStatus.ACTIVE;
    }
}
