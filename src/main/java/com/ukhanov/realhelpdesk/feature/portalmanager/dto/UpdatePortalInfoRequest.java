package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdatePortalInfoRequest {
  @NotBlank(message = "Название портала не может быть пустым")
  @Size(max = 255, message = "Название портала не может превышать 255 символов")
  private String name;
  private String description;

  public UpdatePortalInfoRequest(String name, String description) {
    this.name = name;
    this.description = description;
  }

  public @NotBlank(message = "Название портала не может быть пустым") @Size(max = 255, message = "Название портала не может превышать 255 символов") String getName() {
    return name;
  }

  public void setName(
      @NotBlank(message = "Название портала не может быть пустым") @Size(max = 255, message = "Название портала не может превышать 255 символов") String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
