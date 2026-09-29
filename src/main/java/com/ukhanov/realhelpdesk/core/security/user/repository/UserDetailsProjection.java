package com.ukhanov.realhelpdesk.core.security.user.repository;

public interface UserDetailsProjection {
    Long getId();
    String getFirstName();
    String getLastName();
    String getMiddleName();
    String getEmail();
}
