package com.ukhanov.realhelpdesk.core.security.user.repository;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserModel, Long> {
    Optional<UserModel> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<UserDetailsProjection> findProjectedById(Long id);
    Optional<UserModel> findByRecoveryPasswdToken(Long recoveryPasswdToken);
}
