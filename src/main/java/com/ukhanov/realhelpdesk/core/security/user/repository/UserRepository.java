package com.ukhanov.realhelpdesk.core.security.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;

@Repository
public interface UserRepository extends JpaRepository<UserModel, Long> {
    Optional<UserModel> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<UserDetailsProjection> findProjectedById(Long id);
    Optional<UserModel> findByRecoveryPasswdToken(Long recoveryPasswdToken);
}
