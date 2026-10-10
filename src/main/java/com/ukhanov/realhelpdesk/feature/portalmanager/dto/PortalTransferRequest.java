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
public record PortalTransferRequest(
        @NotBlank(message = "Email нового владельца обязателен") @Email(message = "Некорректный формат Email")
         @Size(max = 255, message = "Email не может превышать 255 символов") String email,
        @NotBlank(message = "Пароль обязателен") @Size(min = 8, message = "Пароль должен быть не менее 8 символов") String password,
        @NotBlank(message = "Причина передачи обязательна") @Size(max = 2000, message = "Причина не может превышать 2000 символов")
         @NoHtml String reason,
        @NotNull(message = "Необходимо указать, остаётся ли текущий владелец участником портала") Boolean keepOldOwnerAsMember) {
}
