package com.ukhanov.realhelpdesk.core.security.auth.tokens.repository;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JwtRefreshTokenRepository extends JpaRepository<RefreshTokenModel, UUID> {
    Optional<RefreshTokenModel> findTopByUserEmailAndStatusOrderByCreatedAtDesc(String email, TokenStatus status);

    //требуется забирать UserModel по refresh-токену
    @EntityGraph(attributePaths = "user")
    Optional<RefreshTokenModel> findByTokenRefresh(String tokenRefresh);

}