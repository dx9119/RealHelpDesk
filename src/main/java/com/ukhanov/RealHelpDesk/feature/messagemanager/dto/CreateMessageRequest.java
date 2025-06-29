package com.ukhanov.RealHelpDesk.feature.messagemanager.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateMessageRequest {

    @NotBlank(message = "Текст сообщения не может быть пустым")
    private String messageText;

    // Геттеры и сеттеры
    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }
}
