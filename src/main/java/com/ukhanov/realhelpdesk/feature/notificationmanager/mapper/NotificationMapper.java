package com.ukhanov.realhelpdesk.feature.notificationmanager.mapper;

import org.mapstruct.Mapper;

import com.ukhanov.realhelpdesk.domain.notification.model.NotificationModel;
import com.ukhanov.realhelpdesk.feature.notificationmanager.dto.NotificationResponse;

/** Маппинг уведомлений пользователя; поля читателя и группы заявок в ответ не попадают. */
@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(NotificationModel model);

}
