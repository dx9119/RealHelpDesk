package com.ukhanov.realhelpdesk.feature.usermanager.dto;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserInfoRequest {

  @NoHtml
  @NotBlank(message = "Наличие имени обязательно")
  @Size(min = 2, max = 20, message = "Имя не может быть короче двух символов")
  private String firstName;

  @NoHtml
  @NotBlank(message = "Наличие фамилии обязательно")
  @Size(min = 2, max = 20, message = "Фамилия не может быть короче двух символов")
  private String lastName;

  @NoHtml
  @Size(max = 20)
  private String middleName;

  @NoHtml
  private String additionalInfo;

  public UserInfoRequest(String firstName, String lastName, String middleName, String additionalInfo) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.middleName = middleName;
    this.additionalInfo = additionalInfo;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getMiddleName() {
    return middleName;
  }

  public void setMiddleName(String middleName) {
    this.middleName = middleName;
  }

  public String getAdditionalInfo() {
    return additionalInfo;
  }

  public void setAdditionalInfo(String additionalInfo) {
    this.additionalInfo = additionalInfo;
  }

}
