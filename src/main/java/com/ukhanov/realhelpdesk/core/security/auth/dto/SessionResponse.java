package com.ukhanov.realhelpdesk.core.security.auth.dto;

import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoResponse;

/**
 * Статус сессии для клиента.
 *
 * <p>
 * Раньше эндпоинт отдавал {@code {"authorization":"Активен"}} — локализованную строку в машинном поле, которая к тому же всегда одна и та
 * же (иначе запрос не дошёл бы до контроллера), а id пользователя приходилось получать вторым запросом {@code GET /users/profile} на каждом
 * старте приложения.
 */
public record SessionResponse(boolean authenticated, UserInfoResponse user) {
}
