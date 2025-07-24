package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

public class PortalInfoResponse {
  Long id;
  String namePortal;
  String description = "none";

  public PortalInfoResponse(Long id, String namePortal) {
    this.id = id;
    this.namePortal = namePortal;
  }

  public PortalInfoResponse(Long id, String namePortal,String description) {
    this.id = id;
    this.namePortal = namePortal;
    this.description = description;
  }

  public Long getId() {
    return id;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getNamePortal() {
    return namePortal;
  }

  public void setNamePortal(String namePortal) {
    this.namePortal = namePortal;
  }

}
