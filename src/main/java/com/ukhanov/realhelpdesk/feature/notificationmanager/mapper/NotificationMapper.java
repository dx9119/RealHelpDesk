package com.ukhanov.realhelpdesk.feature.notificationmanager.mapper;

import com.ukhanov.realhelpdesk.domain.notification.model.NotificationModel;
import com.ukhanov.realhelpdesk.feature.notificationmanager.dto.NotificationResponse;

public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationResponse toResponse(NotificationModel model) {
        if (model == null) {
            return null;
        }
        return new NotificationResponse(model.getId(), model.getEvent(), model.getTicketId(), model.getPortalId(), model.getTitle(),
                model.isRead(), model.getCreatedAt());
    }
}
