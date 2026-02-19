package com.ukhanov.realhelpdesk.feature.usermanager.service;

import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.SaveTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.SetTokenService;
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
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    private SetTokenService setTokenService;

    private UserManageService service;

    @BeforeEach
    void setUp() {
        service = new UserManageService(
                currentUserProvider, userDomainService, emailDeliveryService,
                passwordEncoder, getTokenService, saveTokenService, setTokenService
        );
    }

    // ────────────────────────────────────────────────────────────────
    // getUserInfo
    // ────────────────────────────────────────────────────────────────

    @Test
    void getUserInfo_shouldReturnMappedCurrentUser() {
        UserModel user = createDefaultUser();
        when(currentUserProvider.getCurrentUserModel()).thenReturn(user);

        UserInfoResponse response = service.getUserInfo();

        assertThat(response.getFirstName()).isEqualTo("firstName");
        assertThat(response.getEmail()).isEqualTo("email@example.com");
        assertThat(response).usingRecursiveComparison()
             .isEqualTo(UserMapper.toResponse(user));

        verifyNoInteractions(emailDeliveryService, passwordEncoder, getTokenService, saveTokenService, setTokenService);
    }

    // ────────────────────────────────────────────────────────────────
    // updateUserInfo
    // ────────────────────────────────────────────────────────────────

    @Test
    void updateUserInfo_shouldUpdateFieldsAndReturnUpdatedUser() {
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

        verify(userDomainService).saveUser(argThat(u ->
                "NewFirst".equals(u.getFirstName()) &&
                        "NewLast".equals(u.getLastName()) &&
                        "NewMiddle".equals(u.getMiddleName()) &&
                        "NewInfo".equals(u.getAdditionalInfo())
        ));
    }

    @Test
    void updateUserInfo_shouldThrowNpe_whenRequestIsNull() {
        assertThatThrownBy(() -> service.updateUserInfo(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ────────────────────────────────────────────────────────────────
    // sendResetLink
    // ────────────────────────────────────────────────────────────────


    @Test
    void sendResetLink_shouldThrowWhenUserNotFound() {
        when(userDomainService.getUserByEmail("unknown@example.com"))
                .thenThrow(new EntityNotFoundException("User not found"));

        RecoveryRequest request = new RecoveryRequest("unknown@example.com");

        assertThatThrownBy(() -> service.sendResetLink(request))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoMoreInteractions(emailDeliveryService);
    }

    @Test
    void sendResetLink_shouldThrowNpe_whenRequestIsNull() {
        assertThatThrownBy(() -> service.sendResetLink(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ────────────────────────────────────────────────────────────────
    // setNewPasswd
    // ────────────────────────────────────────────────────────────────

    @Test
    void setNewPasswd_shouldUpdatePasswordAndInvalidateOldTokens() throws Exception {
        UUID code = UUID.randomUUID();
        UserModel user = createDefaultUser();
        user.setRecoveryPasswdToken(code);

        when(userDomainService.getUserByRecoveryPasswdToken(code)).thenReturn(user);

        RefreshTokenModel oldToken = new RefreshTokenModel();
        oldToken.setStatus(TokenStatus.ACTIVE);
        when(getTokenService.getActiveRefreshToken(user)).thenReturn(oldToken);

        when(passwordEncoder.encode("newStrongPass123")).thenReturn("encodedNewPass");

        NewPasswdRequest request = new NewPasswdRequest("newStrongPass123");

        service.setNewPasswd(code, request);

        verify(setTokenService).addNewRefreshToken(user);
        verify(passwordEncoder).encode("newStrongPass123");

        ArgumentCaptor<UserModel> userCaptor = ArgumentCaptor.forClass(UserModel.class);
        verify(userDomainService).saveUser(userCaptor.capture());
        UserModel savedUser = userCaptor.getValue();
        assertThat(savedUser.getPasswordHash()).isEqualTo("encodedNewPass");
        assertThat(savedUser.getRecoveryPasswdToken()).isNotEqualTo(code); // токен сброшен

        ArgumentCaptor<RefreshTokenModel> tokenCaptor = ArgumentCaptor.forClass(RefreshTokenModel.class);
        verify(saveTokenService).saveRefreshToken(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getStatus()).isEqualTo(TokenStatus.PASSWD_CHANGE);
    }

    @Test
    void setNewPasswd_shouldThrowWhenTokenNotFound() {
        UUID wrongCode = UUID.randomUUID();
        when(userDomainService.getUserByRecoveryPasswdToken(wrongCode))
                .thenThrow(new EntityNotFoundException("Token not found"));

        assertThatThrownBy(() -> service.setNewPasswd(wrongCode, new NewPasswdRequest("pass")))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(setTokenService, saveTokenService, passwordEncoder);
    }

    @Test
    void setNewPasswd_shouldThrowNpe_whenRequestIsNull() {
        assertThatThrownBy(() -> service.setNewPasswd(UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void setNewPasswd_shouldThrowNpe_whenCodeIsNull() {
        assertThatThrownBy(() -> service.setNewPasswd(null, new NewPasswdRequest("pass")))
                .isInstanceOf(NullPointerException.class);
    }

    // ────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────

    private UserModel createDefaultUser() {
        UserModel user = new UserModel();
        user.setId(UUID.randomUUID());
        user.setEmail("email@example.com");
        user.setFirstName("firstName");
        user.setLastName("lastName");
        user.setMiddleName("middleName");
        user.setAdditionalInfo("additionalInfo");
        user.setPasswordHash("oldHash");
        user.setUserStatus(UserStatus.ACTIVE);
        user.setUserRole(UserRole.ROLE_NONE);
        user.setUserExternalSource(UserPlatformSource.LOCAL);
        user.setRecoveryPasswdToken(UUID.randomUUID());
        return user;
    }
}