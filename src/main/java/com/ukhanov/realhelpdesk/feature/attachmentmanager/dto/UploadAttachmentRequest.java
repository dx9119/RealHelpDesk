package com.ukhanov.realhelpdesk.feature.attachmentmanager.dto;

import jakarta.validation.constraints.Size;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;

/**
 * Поля формы multipart-загрузки вложения. Сам файл приходит отдельной частью (RequestParam "file"), здесь — только сопроводительный текст
 * сообщения, он необязательный: без него сервис сформирует «Файл: <имя>».
 */
public record UploadAttachmentRequest(
        @Size(max = 8000, message = "Сообщение не может быть больше 8000 символов") @NoHtml String messageText) {
}
