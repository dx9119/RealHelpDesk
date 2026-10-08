package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;

/**
 * Запрос владельца на передачу портала: кому передать, пароль владельца для подтверждения личности, причина и решение — остаётся ли текущий
 * владелец участником портала.
 */
public class PortalTransferRequest {

    @NotBlank(message = "Email нового владельца обязателен")
    @Email(message = "Некорректный формат Email")
    @Size(max = 255, message = "Email не может превышать 255 символов")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, message = "Пароль должен быть не менее 8 символов")
    private String password;

    @NotBlank(message = "Причина передачи обязательна")
    @Size(max = 2000, message = "Причина не может превышать 2000 символов")
    @NoHtml
    private String reason;

    @NotNull(message = "Необходимо указать, остаётся ли текущий владелец участником портала")
    private Boolean keepOldOwnerAsMember;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean getKeepOldOwnerAsMember() {
        return keepOldOwnerAsMember;
    }

    public void setKeepOldOwnerAsMember(Boolean keepOldOwnerAsMember) {
        this.keepOldOwnerAsMember = keepOldOwnerAsMember;
    }
}
