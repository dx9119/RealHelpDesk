package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenBearerResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokensResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.repository.JwtRefreshTokenRepository;
import com.ukhanov.realhelpdesk.core.security.user.SecurityUser;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Ротация refresh-токенов при выдаче новой пары")
class GetTokenServiceTest {

    private static final String EMAIL = "user@test.local";
    private static final String NEW_REFRESH = "new-refresh-token";

    private JwtRefreshTokenRepository jwtRefreshTokenRepository;
    private SaveTokenService saveTokenService;
    private GetTokenService service;
    private UserModel user;

    @BeforeEach
    void setUp() {
        GenTokenService genTokenService = mock(GenTokenService.class);
        saveTokenService = mock(SaveTokenService.class);
        jwtRefreshTokenRepository = mock(JwtRefreshTokenRepository.class);

        service = new GetTokenService(genTokenService, saveTokenService, jwtRefreshTokenRepository, mock(FindTokenService.class),
                mock(ValidTokenService.class), mock(DecodeTokenService.class));

        user = new UserModel();
        user.setId(7L);
        user.setEmail(EMAIL);

        TokenBearerResponse access = new TokenBearerResponse("access-token");
        when(genTokenService.generateAccessJwtToken(any(SecurityUser.class))).thenReturn(access);

        RefreshTokenModel newToken = new RefreshTokenModel();
        newToken.setRawToken(NEW_REFRESH);
        when(genTokenService.generateRefreshJwtToken(any(SecurityUser.class))).thenReturn(newToken);

        when(jwtRefreshTokenRepository.findAllByUserEmailAndStatus(EMAIL, TokenStatus.ACTIVE)).thenReturn(List.of());
    }

    @Test
    @DisplayName("Предыдущие активные refresh-токены отзываются — старый токен перестаёт быть валидным")
    void previousActiveTokens_areRevokedOnIssuance() {
        RefreshTokenModel previousSession = activeToken();
        RefreshTokenModel staleToken = activeToken();
        when(jwtRefreshTokenRepository.findAllByUserEmailAndStatus(EMAIL, TokenStatus.ACTIVE))
                .thenReturn(List.of(previousSession, staleToken));

        TokensResponse response = service.getNewTokens(user);

        assertThat(previousSession.getStatus()).isEqualTo(TokenStatus.REVOKED);
        assertThat(staleToken.getStatus()).isEqualTo(TokenStatus.REVOKED);
        assertThat(response.refreshToken()).isEqualTo(NEW_REFRESH);
        verify(saveTokenService, times(3)).saveRefreshToken(any(RefreshTokenModel.class));
    }

    @Test
    @DisplayName("Активных токенов нет — отзывать нечего, сохраняется только новый")
    void noActiveTokens_nothingRevoked() {
        service.getNewTokens(user);

        verify(jwtRefreshTokenRepository).findAllByUserEmailAndStatus(EMAIL, TokenStatus.ACTIVE);
        verify(saveTokenService, times(1)).saveRefreshToken(any(RefreshTokenModel.class));
        verify(saveTokenService, never()).saveRefreshToken(argThat(token -> token.getStatus() == TokenStatus.REVOKED));
    }

    private RefreshTokenModel activeToken() {
        RefreshTokenModel token = new RefreshTokenModel();
        token.setStatus(TokenStatus.ACTIVE);
        token.setRawToken("old-refresh-token");
        return token;
    }
}
