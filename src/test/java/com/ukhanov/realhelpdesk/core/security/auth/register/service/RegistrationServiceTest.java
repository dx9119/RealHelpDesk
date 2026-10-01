package com.ukhanov.realhelpdesk.core.security.auth.register.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import com.ukhanov.realhelpdesk.core.security.auth.register.dto.RegisterRequest;
import com.ukhanov.realhelpdesk.core.security.auth.register.exception.RegistrationException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.captcha.service.CaptchaService;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Регистрация: письма подтверждения адреса")
class RegistrationServiceTest {

    private static final String EMAIL = "newuser@example.com";
    private static final String PASSWORD = "strongPass1";

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserDomainService userDomainService;

    @Mock
    private GetTokenService getTokenService;

    @Mock
    private EmailDeliveryService emailDeliveryService;

    @Mock
    private CaptchaService captchaService;

    private RegistrationService service;

    private final EmailTemplates emailTemplates = EmailTemplatesFixture.emailTemplates();

    @BeforeEach
    void setUp() {
        service = new RegistrationService(passwordEncoder, userDomainService, getTokenService, emailDeliveryService, captchaService,
                emailTemplates);
    }

    @Test
    @DisplayName("Успешная регистрация: уведомление админу и письмо подтверждения пользователю")
    void addUser_sendsAdminAndConfirmationEmails() throws Exception {
        when(userDomainService.isUserExistsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-hash");
        when(userDomainService.saveUser(any())).thenAnswer(invocation -> {
            UserModel user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        UserModel user = service.addUser(request());

        verify(emailDeliveryService).sendAdminNotification(eq("Новая регистрация:" + EMAIL), anyString(),
                eq(NotificationEvent.NEW_SYSTEM_MESSAGE));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailDeliveryService).sendUserNotification(eq(EMAIL), eq(emailTemplates.registrationLinkSubject()), bodyCaptor.capture(),
                eq(NotificationEvent.NEW_SYSTEM_MESSAGE));

        assertThat(user.getVerifyEmailToken()).isNotNull();
        assertThat(bodyCaptor.getValue()).contains(user.getVerifyEmailToken().toString())
                .contains("https://" + EmailTemplatesFixture.DOMAIN + "/notify-settings");
    }

    @Test
    @DisplayName("Занятая почта: ошибка и ни одного письма")
    void addUser_duplicateEmail_throwsWithoutEmails() {
        when(userDomainService.isUserExistsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.addUser(request())).isInstanceOf(RegistrationException.class)
                .hasMessageContaining("Почта уже используется");

        verifyNoInteractions(emailDeliveryService, passwordEncoder, getTokenService);
    }

    @Test
    @DisplayName("Короткий пароль: ошибка и ни одного письма")
    void addUser_shortPassword_throwsWithoutEmails() {
        when(userDomainService.isUserExistsByEmail(EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> service.addUser(new RegisterRequest("Имя", "Фамилия", EMAIL, "short")))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("Минимальная длина пароля");

        verifyNoInteractions(emailDeliveryService, passwordEncoder, getTokenService);
    }

    @Test
    @DisplayName("null-запрос — ошибка")
    void addUser_nullRequest_throwsNpe() {
        assertThatThrownBy(() -> service.addUser(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Непройденная капча: пользователь не создаётся, письма не уходят")
    void processRegistration_captchaFailed_noUserCreated() throws Exception {
        doThrow(new CaptchaException("Провал прохождения капчи")).when(captchaService).captVerificationResult(anyString(), any());

        assertThatThrownBy(() -> service.processRegistration(request(), "cap-1")).isInstanceOf(CaptchaException.class);

        verifyNoInteractions(userDomainService, emailDeliveryService, getTokenService);
    }

    private RegisterRequest request() {
        return new RegisterRequest("Имя", "Фамилия", EMAIL, PASSWORD);
    }
}
