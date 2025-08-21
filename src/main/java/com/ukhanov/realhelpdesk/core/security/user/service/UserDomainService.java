package com.ukhanov.realhelpdesk.core.security.user.service;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.repository.UserDetailsProjection;
import com.ukhanov.realhelpdesk.core.security.user.repository.UserRepository;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketLiveStatus;
import jakarta.transaction.Transactional;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class UserDomainService {

    private final UserRepository userRepository;

    public UserDomainService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private UserModel resolveUserByIdentifier(String identifier, boolean isEmail) {
        Objects.requireNonNull(identifier, "Идентификатор не может быть null");


        if (isEmail) {
            return userRepository.findByEmail(identifier)
                    .orElseThrow(() -> new UsernameNotFoundException("Не найден пользователь с email: " + identifier));
        } else {
            UUID id = UUID.fromString(identifier);
            return userRepository.findById(id)
                    .orElseThrow(() -> new UsernameNotFoundException("Не найден пользователь с ID: " + identifier));
        }

    }

    public UserModel getUserByEmail(String email) {
        return resolveUserByIdentifier(email, true);
    }

    public UserModel getUserById(UUID userId) {
        return resolveUserByIdentifier(userId.toString(), false);
    }

    public boolean isUserExistsByEmail(String email) {
        Objects.requireNonNull(email, "Email не может быть null");

        return userRepository.existsByEmail(email);
    }

    @Transactional
    public UserModel saveUser(UserModel user) {
        Objects.requireNonNull(user, "Пользователь не может быть null");

        return userRepository.save(user);
    }

    public UserDetailsProjection getUserDetailsById(UUID userId) {
        Objects.requireNonNull(userId, "ID пользователя не может быть null");

        return userRepository.findProjectedById(userId)
            .orElseThrow(() -> new UsernameNotFoundException("Не найден пользователь с ID: " + userId));
    }

    public UserModel getUserByRecoveryPasswdToken (UUID code){
        Objects.requireNonNull(code,"Код обязателен");

        return userRepository.findByRecoveryPasswdToken(code)
                .orElseThrow(() -> new UsernameNotFoundException("Неизвестный код восстановления" + code));
    }

}
