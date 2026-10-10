package com.ukhanov.realhelpdesk.core.mail.dto;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;

public record EmailInfoResponse(NotificationEvent muteLevel) {
}
