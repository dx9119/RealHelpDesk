package com.ukhanov.realhelpdesk.core.security.auth.tokens.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;

@Repository
public interface JwtRefreshTokenRepository extends JpaRepository<RefreshTokenModel, UUID> {
    List<RefreshTokenModel> findAllByUserEmailAndStatus(String email, TokenStatus status);

    /**
     * Bulk-отзыв одним UPDATE: вместо N селектов и N сохранений сущностей при ротации или смене пароля.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
                UPDATE RefreshTokenModel t
                SET t.status = :nextStatus
                WHERE t.user.email = :email
                  AND t.status = :currentStatus
            """)
    int updateStatusByEmailAndStatus(@Param("email") String email, @Param("currentStatus") TokenStatus currentStatus,
            @Param("nextStatus") TokenStatus nextStatus);

    // требуется забирать UserModel по refresh-токену
    @EntityGraph(attributePaths = "user")
    Optional<RefreshTokenModel> findByTokenRefresh(String tokenRefresh);

}
