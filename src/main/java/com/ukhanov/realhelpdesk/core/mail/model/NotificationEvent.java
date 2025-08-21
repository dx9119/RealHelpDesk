package com.ukhanov.realhelpdesk.core.mail.model;

public enum NotificationEvent {
  NEW_TICKET,
  NEW_MESSAGE,
  NEW_SYSTEM_MESSAGE,
  NEW_TICKET_OR_MESSAGE,
  NEW_PORTAL,
  CHANGE_TICKET,
  RECOVERY_PASSWORD,
  TICKET_DELETED,
  PORTAL_DELETED,
  NONE;
}
