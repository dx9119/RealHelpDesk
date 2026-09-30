package com.ukhanov.realhelpdesk.feature.usermanager.service;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ukhanov.realhelpdesk.core.mail.model.EmailTemplates;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.mail.support.EmailTemplatesFixture;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.SaveTokenService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.model.UserPlatformSource;
import com.ukhanov.realhelpdesk.core.security.user.model.UserRole;
import com.ukhanov.realhelpdesk.core.security.user.model.UserStatus;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.NewPasswdRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.RecoveryRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoResponse;
import com.ukhanov.realhelpdesk.feature.usermanager.mapper.UserMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class UserManageServiceTest {

    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserDomainService userDomainService;
    @Mock
    private EmailDeliveryService emailDeliveryService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private GetTokenService getTokenService;
    @Mock
    private SaveTokenService saveTokenService;
    @Mock

    private UserManageService service;

    private final EmailTemplates emailTemplates = EmailTemplatesFixture.emailTemplates();

    @BeforeEach
    void setUp() {
        service = new UserManageService(currentUserProvider, userDomainService, emailDeliveryService, passwordEncoder, getTokenService,
                saveTokenService, emailTemplates);
    }

    // ────────────────────────────────────────────────────────────────
    // getUserInfo
    // ────────────────────────────────────────────────────────────────

    @Test
    void getUserInfo_returnsMappedCurrentUser() {
        UserModel user = createDefaultUser();
        when(currentUserProvider.getCurrentUserModel()).thenReturn(user);

        UserInfoResponse response = service.getUserInfo();

        assertThat(response.getFirstName()).isEqualTo("firstName");
        assertThat(response.getEmail()).isEqualTo("email@example.com");
        assertThat(response).usingRecursiveComparison().isEqualTo(UserMapper.toResponse(user));

        verifyNoInteractions(emailDeliveryService, passwordEncoder, getTokenService, saveTokenService);
    }

    // ────────────────────────────────────────────────────────────────
    // updateUserInfo
    // ────────────────────────────────────────────────────────────────

    @Test
    void updateUserInfo_success_updatesFieldsAndReturnsUpdatedUser() {
        UserModel existing = createDefaultUser();
        when(currentUserProvider.getCurrentUserModel()).thenReturn(existing);

        UserModel saved = createDefaultUser();
        saved.setFirstName("NewFirst");
        saved.setLastName("NewLast");
        when(userDomainService.saveUser(any())).thenReturn(saved);

        UserInfoRequest request = new UserInfoRequest("NewFirst", "NewLast", "NewMiddle", "NewInfo");

        UserInfoResponse response = service.updateUserInfo(request);

        assertThat(response.getFirstName()).isEqualTo("NewFirst");
        assertThat(response.getLastName()).isEqualTo("NewLast");

        verify(userDomainService).saveUser(argThat(u -> "NewFirst".equals(u.getFirstName()) && "NewLast".equals(u.getLastName())
                && "NewMiddle".equals(u.getMiddleName()) && "NewInfo".equals(u.getAdditionalInfo())));
    }

    @Test
    void updateUserInfo_requestNull_throwsNpe() {
        assertThatThrownBy(() -> service.updateUserInfo(null)).isInstanceOf(NullPointerException.class);
    }

    // ────────────────────────────────────────────────────────────────
    // sendResetLink
    // ────────────────────────────────────────────────────────────────

    @Test
    void sendResetLink_userNotFound_throws() {
        when(userDomainService.getUserByEmail("unknown@example.com")).thenThrow(new EntityNotFoundException("User not found"));

        RecoveryRequest request = new RecoveryRequest("unknown@example.com");

        assertThatThrownBy(() -> service.sendResetLink(request)).isInstanceOf(EntityNotFoundException.class);

        verifyNoMoreInteractions(emailDeliveryService);
    }

    @Test
    void sendResetLink_requestNull_throwsNpe() {
        assertThatThrownBy(() -> service.sendResetLink(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void sendResetLink_success_sendsPasswordResetEmailWithFreshToken() throws Exception {
        UserModel user = createDefaultUser();
        when(userDomainService.getUserByEmail("email@example.com")).thenReturn(user);

        service.sendResetLink(new RecoveryRequest("email@example.com"));

        verify(userDomainService).saveUser(user);
        assertThat(user.getRecoveryPasswdToken()).isNotEqualTo(88L);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailDeliveryService).sendEmail(eq("email@example.com"), eq(emailTemplates.passwordResetSubject()), bodyCaptor.capture(),
                eq(NotificationEvent.RECOVERY_PASSWORD));
        verifyNoMoreInteractions(emailDeliveryService);

        assertThat(bodyCaptor.getValue()).contains("/pass-reset?code=" + user.getRecoveryPasswdToken()).contains(EmailTemplates.DOMAIN);
    }

    // ────────────────────────────────────────────────────────────────
    // setNewPasswd
    // ────────────────────────────────────────────────────────────────

    @Test
    void setNewPasswd_success_updatesPasswordAndInvalidatesOldTokens() throws Exception {
        Long code = 55L;
        UserModel user = createDefaultUser();
        user.setRecoveryPasswdToken(code);
        int tokenVersionBefore = user.getTokenVersion();

        when(userDomainService.getUserByRecoveryPasswdToken(code)).thenReturn(user);

        // Старые активные refresh-токены (их должно быть несколько — например, с разных устройств)
        RefreshTokenModel oldToken1 = new RefreshTokenModel();
        oldToken1.setStatus(TokenStatus.ACTIVE);
        RefreshTokenModel oldToken2 = new RefreshTokenModel();
        oldToken2.setStatus(TokenStatus.ACTIVE);
        when(getTokenService.getActiveRefreshTokens(user)).thenReturn(java.util.List.of(oldToken1, oldToken2));

        when(passwordEncoder.encode("newStrongPass123")).thenReturn("encodedNewPass");

        NewPasswdRequest request = new NewPasswdRequest("newStrongPass123");

        service.setNewPasswd(code, request);

        verify(passwordEncoder).encode("newStrongPass123");

        ArgumentCaptor<UserModel> userCaptor = ArgumentCaptor.forClass(UserModel.class);
        verify(userDomainService).saveUser(userCaptor.capture());
        UserModel savedUser = userCaptor.getValue();
        assertThat(savedUser.getPasswordHash()).isEqualTo("encodedNewPass");
        assertThat(savedUser.getRecoveryPasswdToken()).isNotEqualTo(code); // токен сброшен
        assertThat(savedUser.getTokenVersion()).isEqualTo(tokenVersionBefore + 1); // access-токены отозваны

        // Отзываются ВСЕ активные refresh-токены, а не только последний
        ArgumentCaptor<RefreshTokenModel> tokenCaptor = ArgumentCaptor.forClass(RefreshTokenModel.class);
        verify(saveTokenService, times(2)).saveRefreshToken(tokenCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(tokenCaptor.getAllValues()).hasSize(2)
                .allMatch(token -> token.getStatus() == TokenStatus.PASSWD_CHANGE);
    }

    @Test
    void setNewPasswd_tokenNotFound_throws() {
        Long wrongCode = 66L;
        when(userDomainService.getUserByRecoveryPasswdToken(wrongCode)).thenThrow(new EntityNotFoundException("Token not found"));

        assertThatThrownBy(() -> service.setNewPasswd(wrongCode, new NewPasswdRequest("pass"))).isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(saveTokenService, passwordEncoder);
    }

    @Test
    void setNewPasswd_requestNull_throwsNpe() {
        assertThatThrownBy(() -> service.setNewPasswd(55L, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void setNewPasswd_codeNull_throwsNpe() {
        assertThatThrownBy(() -> service.setNewPasswd(null, new NewPasswdRequest("pass"))).isInstanceOf(NullPointerException.class);
    }

    // ────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────

    private UserModel createDefaultUser() {
        UserModel user = new UserModel();
        user.setId(77L);
        user.setEmail("email@example.com");
        user.setFirstName("firstName");
        user.setLastName("lastName");
        user.setMiddleName("middleName");
        user.setAdditionalInfo("additionalInfo");
        user.setPasswordHash("oldHash");
        user.setUserStatus(UserStatus.ACTIVE);
        user.setUserRole(UserRole.ROLE_NONE);
        user.setUserExternalSource(UserPlatformSource.LOCAL);
        user.setRecoveryPasswdToken(88L);
        return user;
    }
}
