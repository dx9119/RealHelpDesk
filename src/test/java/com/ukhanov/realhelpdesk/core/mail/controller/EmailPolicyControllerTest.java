package com.ukhanov.realhelpdesk.core.mail.controller;

import jakarta.mail.MessagingException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ukhanov.realhelpdesk.core.config.ClientIpProperties;
import com.ukhanov.realhelpdesk.core.exception.GlobalExceptionHandler;
import com.ukhanov.realhelpdesk.core.http.ClientIpResolver;
import com.ukhanov.realhelpdesk.core.mail.dto.EmailInfoResponse;
import com.ukhanov.realhelpdesk.core.mail.exception.EmailAccessDeniedException;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.mail.service.EmailPolicyService;
import com.ukhanov.realhelpdesk.core.security.localjwt.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.localjwt.captcha.exception.CaptchaReason;
import com.ukhanov.realhelpdesk.core.security.localjwt.captcha.service.CaptchaService;
import com.ukhanov.realhelpdesk.core.security.localjwt.captcha.service.CaptchaTokenService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("REST-контракт почтовой политики (EmailPolicyController)")
class EmailPolicyControllerTest {

    @Mock
    private EmailDeliveryService emailDeliveryService;

    @Mock
    private EmailPolicyService emailPolicyService;

    @Mock
    private CaptchaService captchaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EmailPolicyController(emailDeliveryService, emailPolicyService, captchaService,
                new ClientIpResolver(new ClientIpProperties()))).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    // ────────────────────────────────────────────────
    // Подтверждение адреса
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /email/confirmations/{token} → 204")
    void confirmEmail_returnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/email/confirmations/123456")).andExpect(status().isNoContent());

        verify(emailDeliveryService).confirmEmail(123456L);
    }

    @Test
    @DisplayName("Неверный код подтверждения → 403 (problem+json)")
    void confirmEmail_wrongToken_returnsForbidden() throws Exception {
        doThrow(new EmailAccessDeniedException("Код подтверждения права владения почтой не верный")).when(emailDeliveryService)
                .confirmEmail(123456L);

        mockMvc.perform(post("/api/v1/email/confirmations/123456")).andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Код подтверждения права владения почтой не верный"));
    }

    @Test
    @DisplayName("Код подтверждения не число → 400, сервис не вызывается")
    void confirmEmail_nonNumericToken_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/email/confirmations/abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailDeliveryService, never()).confirmEmail(any());
    }

    // ────────────────────────────────────────────────
    // Информация об отписках
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /email/info → 204? нет, 200 с текущим уровнем глушения")
    void getInfo_returnsMuteLevel() throws Exception {
        EmailInfoResponse response = new EmailInfoResponse(NotificationEvent.NEW_TICKET);
        when(emailPolicyService.getEmailInfo()).thenReturn(response);

        mockMvc.perform(get("/api/v1/email/info")).andExpect(status().isOk()).andExpect(jsonPath("$.muteLevel").value("NEW_TICKET"));
    }

    // ────────────────────────────────────────────────
    // Отписка от уведомлений
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /email/notifications/{event} → 204 и событие сохраняется")
    void stopNotifications_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/email/notifications/CHANGE_TICKET")).andExpect(status().isNoContent());

        verify(emailPolicyService).addToStopList(NotificationEvent.CHANGE_TICKET);
    }

    @Test
    @DisplayName("Неизвестное событие → 400, стоп-лист не меняется")
    void stopNotifications_unknownEvent_returnsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/v1/email/notifications/NOT_AN_EVENT")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailPolicyService, never()).addToStopList(any());
    }

    // ────────────────────────────────────────────────
    // Код подтверждения
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /email/codes c X-Captcha-Token → 202, токен проверен, письмо отправлено")
    void sendConfirmCode_returnsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/email/codes").header("X-Captcha-Token", "jwt-token")).andExpect(status().isAccepted());

        verify(captchaService).verify("jwt-token", CaptchaTokenService.ACTION_EMAIL_CODE, "127.0.0.1");
        verify(emailDeliveryService).sendConfirmCode();
    }

    @Test
    @DisplayName("Капча не пройдена → 400 с code, письмо не отправляется")
    void sendConfirmCode_captchaFailed_returnsBadRequest() throws Exception {
        doThrow(new CaptchaException("Провал прохождения капчи", CaptchaReason.POW_FAILED)).when(captchaService).verify(any(),
                eq(CaptchaTokenService.ACTION_EMAIL_CODE), any());

        mockMvc.perform(post("/api/v1/email/codes").header("X-Captcha-Token", "bad")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Провал прохождения капчи")).andExpect(jsonPath("$.code").value("pow_failed"));

        verify(emailDeliveryService, never()).sendConfirmCode();
    }

    @Test
    @DisplayName("Почтовый сервер недоступен → 503 (MailSendException)")
    void sendConfirmCode_mailServerDown_returnsServiceUnavailable() throws Exception {
        doThrow(new MailSendException("Connection refused")).when(emailDeliveryService).sendConfirmCode();

        mockMvc.perform(post("/api/v1/email/codes").header("X-Captcha-Token", "jwt-token")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503)).andExpect(jsonPath("$.detail").value("Ошибка отправки email"));
    }

    @Test
    @DisplayName("Ошибка MIME/SMTP → 503 (MessagingException)")
    void sendConfirmCode_messagingFailure_returnsServiceUnavailable() throws Exception {
        doThrow(new MessagingException("SMTP banner mismatch")).when(emailDeliveryService).sendConfirmCode();

        mockMvc.perform(post("/api/v1/email/codes").header("X-Captcha-Token", "jwt-token")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Ошибка отправки email"));
    }

    @Test
    @DisplayName("Исчерпан лимит писем → 429 (EmailAccessDeniedException c TOO_MANY_REQUESTS)")
    void sendConfirmCode_rateLimited_returnsTooManyRequests() throws Exception {
        doThrow(new EmailAccessDeniedException("Исчерпан лимит", HttpStatus.TOO_MANY_REQUESTS)).when(emailDeliveryService)
                .sendConfirmCode();

        mockMvc.perform(post("/api/v1/email/codes").header("X-Captcha-Token", "jwt-token")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429)).andExpect(jsonPath("$.detail").value("Исчерпан лимит"));
    }

    @Test
    @DisplayName("Без заголовка капчи сервис кода вызывается, проверку выполняет капча")
    void sendConfirmCode_withoutCaptchaHeader_stillCallsService() throws Exception {
        mockMvc.perform(post("/api/v1/email/codes")).andExpect(status().isAccepted());

        verify(captchaService).verify(null, CaptchaTokenService.ACTION_EMAIL_CODE, "127.0.0.1");
        verify(emailDeliveryService).sendConfirmCode();
    }

    @Test
    @DisplayName("Неизвестный маршрут почтовой политики → 404")
    void unknownRoute_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/email/unknown")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("eq-проверка: событие передаётся в сервис без изменений")
    void stopNotifications_passesEventThrough() throws Exception {
        mockMvc.perform(delete("/api/v1/email/notifications/{event}", NotificationEvent.NEW_TICKET_OR_MESSAGE))
                .andExpect(status().isNoContent());

        verify(emailPolicyService).addToStopList(eq(NotificationEvent.NEW_TICKET_OR_MESSAGE));
    }
}
