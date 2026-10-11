package com.ukhanov.realhelpdesk.feature.usermanager.dto;

import jakarta.validation.constraints.Size;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;

/**
 * Частичное обновление профиля: меняются только переданные поля ({@code null} — «не трогать»). Для полной замены — {@code PUT} с
 * {@code UserInfoRequest}.
 */
public record PatchUserInfoRequest(@NoHtml @Size(min = 2, max = 20, message = "Имя не может быть короче двух символов") String firstName,
        @NoHtml @Size(min = 2, max = 20, message = "Фамилия не может быть короче двух символов") String lastName,
        @NoHtml @Size(max = 20) String middleName, @NoHtml String additionalInfo) {

    public boolean isEmpty() {
        return firstName == null && lastName == null && middleName == null && additionalInfo == null;
    }
}
