package com.ukhanov.realhelpdesk.core.security.auth.tokens.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ukhanov.realhelpdesk.core.exception.GlobalExceptionHandler;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.RefreshTokenCleanupService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.RefreshTokenCleanupService.CleanupResult;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("REST-контракт операторской очистки токенов (TokenOperatorController)")
class TokenOperatorControllerTest {

    @Mock
    private RefreshTokenCleanupService refreshTokenCleanupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TokenOperatorController(refreshTokenCleanupService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("POST /operator/tokens/cleanup → 200 со счётчиками удалённых отозванных и батчей")
    void cleanupRevokedTokens_returnsCounters() throws Exception {
        when(refreshTokenCleanupService.cleanRevokedTokens()).thenReturn(new CleanupResult(42, 3));

        mockMvc.perform(post("/api/v1/operator/tokens/cleanup")).andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedDeleted").value(42)).andExpect(jsonPath("$.batches").value(3));

        verify(refreshTokenCleanupService).cleanRevokedTokens();
    }
}
