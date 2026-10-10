package com.ukhanov.realhelpdesk.feature.usermanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;

public record UserInfoRequest(
        @NoHtml @NotBlank(message = "Наличие имени обязательно")
         @Size(min = 2, max = 20, message = "Имя не может быть короче двух символов") String firstName,
        @NoHtml @NotBlank(message = "Наличие фамилии обязательно")
         @Size(min = 2, max = 20, message = "Фамилия не может быть короче двух символов") String lastName,
        @NoHtml @Size(max = 20) String middleName, @NoHtml String additionalInfo) {
}
