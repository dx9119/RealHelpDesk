package com.ukhanov.realhelpdesk.core.security.auth;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ukhanov.realhelpdesk.core.config.JwtProperties;
import com.ukhanov.realhelpdesk.core.security.auth.login.service.LoginService;
import com.ukhanov.realhelpdesk.core.security.auth.logout.service.LogoutService;
import com.ukhanov.realhelpdesk.core.security.auth.refresh.service.RefreshService;
import com.ukhanov.realhelpdesk.core.security.auth.register.service.RegistrationService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Атрибут Domain у auth-cookie из jwt.cookie.domain: задан — общий для поддоменов (файлы на отдельном домене); пусто — cookie host-only,
 * как раньше.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Атрибут Domain у auth-cookie (jwt.cookie.domain)")
class AuthControllerCookieDomainTest {

    @Mock
    private RegistrationService registrationService;
    @Mock
    private LoginService loginService;
    @Mock
    private LogoutService logoutService;
    @Mock
    private GetTokenService getTokenService;
    @Mock
    private RefreshService refreshService;

    private JwtProperties jwtProperties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AuthController(registrationService, loginService, logoutService, getTokenService, refreshService, jwtProperties))
                .build();
    }

    @Test
    @DisplayName("jwt.cookie.domain задан — обе cookie (access и refresh) несут Domain=...")
    void domainConfigured_bothCookiesCarryDomain() throws Exception {
        jwtProperties.getCookie().setDomain(".example.com");

        List<String> cookies = setCookies();

        assertThat(cookies).hasSize(2).allSatisfy(cookie -> assertThat(cookie).contains("Domain=.example.com"));
        assertThat(cookies.get(0)).startsWith("accessToken=");
        assertThat(cookies.get(1)).startsWith("refreshToken=");
    }

    @Test
    @DisplayName("jwt.cookie.domain пуст — атрибут Domain не ставится, cookie остаётся host-only")
    void domainAbsent_noDomainAttribute() throws Exception {
        List<String> cookies = setCookies();

        assertThat(cookies).hasSize(2).noneSatisfy(cookie -> assertThat(cookie).containsIgnoringCase("Domain="));
    }

    private List<String> setCookies() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/v1/auth/session")).andExpect(status().isNoContent()).andReturn();
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    }
}
