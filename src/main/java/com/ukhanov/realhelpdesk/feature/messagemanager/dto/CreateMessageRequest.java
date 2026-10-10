package com.ukhanov.realhelpdesk.feature.messagemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;

public record CreateMessageRequest(
        @NotBlank(message = "Текст сообщения не может быть пустым")
         @Size(max = 8000, message = "Сообщение не может быть больше 8000 символов") @NoHtml String messageText) {
}
