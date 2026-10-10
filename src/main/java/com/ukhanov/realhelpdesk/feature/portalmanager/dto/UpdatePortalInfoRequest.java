package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;

public record UpdatePortalInfoRequest(
        @NotBlank(message = "Название портала не может быть пустым")
         @Size(max = 255, message = "Название портала не может превышать 255 символов") @NoHtml String name,
        @Size(max = 2500, message = "Описание не может превышать 2500 символов") @NoHtml String description) {
}
