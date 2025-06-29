package com.ukhanov.RealHelpDesk.core.security.user.service;

import com.ukhanov.RealHelpDesk.core.security.user.model.UserModel;
import com.ukhanov.RealHelpDesk.core.security.user.repository.UserRepository;
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
        Objects.requireNonNull(identifier, "Identifier cannot be null");

        if (isEmail) {
            return userRepository.findByEmail(identifier)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + identifier));
        } else {
            UUID id = UUID.fromString(identifier);
            return userRepository.findById(id)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + identifier));
        }

    }

    public UserModel getUserByEmail(String email) {
        return resolveUserByIdentifier(email, true);
    }

    public UserModel getUserById(UUID userId) {
        return resolveUserByIdentifier(userId.toString(), false);
    }

    public boolean isUserExistsByEmail(String email) {
        Objects.requireNonNull(email, "email cannot be null");
        return userRepository.existsByEmail(email);
    }

    public UserModel saveUser(UserModel user) {
        Objects.requireNonNull(user, "user cannot be null");
        return userRepository.save(user);
    }


}
