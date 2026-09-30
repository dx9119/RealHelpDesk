package com.ukhanov.realhelpdesk.core.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Маскировка секретных сегментов пути: LogSanitizer")
class LogSanitizerTest {

    @Test
    @DisplayName("Код подтверждения email в пути маскируется")
    void uri_emailConfirmationToken_isMasked() {
        assertThat(LogSanitizer.uri("/api/v1/email/confirmations/1234567890")).isEqualTo("/api/v1/email/confirmations/***");
    }

    @Test
    @DisplayName("Код восстановления пароля в пути маскируется")
    void uri_passwordResetCode_isMasked() {
        assertThat(LogSanitizer.uri("/api/v1/users/password-resets/-987654321012345")).isEqualTo("/api/v1/users/password-resets/***");
    }

    @Test
    @DisplayName("Обычные маршруты не изменяются")
    void uri_regularPaths_areUnchanged() {
        assertThat(LogSanitizer.uri("/api/v1/email/info")).isEqualTo("/api/v1/email/info");
        assertThat(LogSanitizer.uri("/api/v1/users")).isEqualTo("/api/v1/users");
    }

    @Test
    @DisplayName("null вместо пути — пустая строка")
    void uri_null_returnsEmptyString() {
        assertThat(LogSanitizer.uri(null)).isEmpty();
    }
}
