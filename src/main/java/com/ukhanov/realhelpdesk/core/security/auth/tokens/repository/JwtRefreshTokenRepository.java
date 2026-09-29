package com.ukhanov.realhelpdesk.core.security.auth.tokens.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;

@Repository
public interface JwtRefreshTokenRepository extends JpaRepository<RefreshTokenModel, UUID> {
    List<RefreshTokenModel> findAllByUserEmailAndStatus(String email, TokenStatus status);

    // требуется забирать UserModel по refresh-токену
    @EntityGraph(attributePaths = "user")
    Optional<RefreshTokenModel> findByTokenRefresh(String tokenRefresh);

}
