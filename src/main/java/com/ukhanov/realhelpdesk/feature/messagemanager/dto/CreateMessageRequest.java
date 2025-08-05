package com.ukhanov.realhelpdesk.feature.messagemanager.dto;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateMessageRequest {

    @NotBlank(message = "Текст сообщения не может быть пустым")
    @Size(max = 8000, message = "Сообщение не может быть больше 8000 символов")
    @NoHtml
    private String messageText;

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }
}
