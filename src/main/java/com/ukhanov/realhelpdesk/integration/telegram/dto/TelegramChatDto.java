package com.ukhanov.realhelpdesk.integration.telegram.dto;

public class TelegramChatDto {

  // Имена полей соответствуют camelCase ключам в JavaScript payload
  private Long id;
  private String type;
  private String title;
  private String username;
  private String photo_url;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPhoto_url() {
    return photo_url;
  }

  public void setPhoto_url(String photo_url) {
    this.photo_url = photo_url;
  }

}
