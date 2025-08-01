package com.ukhanov.realhelpdesk.core.security.user.repository;

import java.util.UUID;

public interface UserDetailsProjection {
  UUID getId();
  String getFirstName();
  String getLastName();
  String getMiddleName();
  String getEmail();
}
