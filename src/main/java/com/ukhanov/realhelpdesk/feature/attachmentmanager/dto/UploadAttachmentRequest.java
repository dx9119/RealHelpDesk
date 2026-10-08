package com.ukhanov.realhelpdesk.feature.attachmentmanager.dto;

import jakarta.validation.constraints.Size;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;

/**
 * Поля формы multipart-загрузки вложения. Сам файл приходит отдельной частью (RequestParam "file"), здесь — только сопроводительный текст
 * сообщения, он необязательный: без него сервис сформирует «Файл: <имя>».
 */
public class UploadAttachmentRequest {

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
