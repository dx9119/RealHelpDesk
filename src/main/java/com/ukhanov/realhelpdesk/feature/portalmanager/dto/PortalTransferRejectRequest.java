package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;

/** Отклонение передачи портала предлагаемым владельцем: причина уходит инициатору и пишется в историю. */
public class PortalTransferRejectRequest {

    @NotBlank(message = "Причина обязательна")
    @Size(max = 2000, message = "Причина не может превышать 2000 символов")
    @NoHtml
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
