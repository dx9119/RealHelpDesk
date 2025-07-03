package com.ukhanov.RealHelpDesk.core.security.user.repository;

import com.ukhanov.RealHelpDesk.core.security.user.model.UserModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserModel, UUID> {
    Optional<UserModel> findByEmail(String email);
    boolean existsByEmail(String email);
}
