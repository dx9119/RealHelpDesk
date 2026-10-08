package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;

/** Подтверждение передачи портала новым владельцем: свой пароль и причина принятия (пишется в историю). */
public class PortalTransferConfirmRequest {

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, message = "Пароль должен быть не менее 8 символов")
    private String password;

    @NotBlank(message = "Причина обязательна")
    @Size(max = 2000, message = "Причина не может превышать 2000 символов")
    @NoHtml
    private String reason;

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
}
