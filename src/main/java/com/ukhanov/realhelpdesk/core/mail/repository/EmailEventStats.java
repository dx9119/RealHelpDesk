package com.ukhanov.realhelpdesk.core.mail.repository;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;

public interface EmailEventStats {
    NotificationEvent getEvent();
    Long getCount();
}

