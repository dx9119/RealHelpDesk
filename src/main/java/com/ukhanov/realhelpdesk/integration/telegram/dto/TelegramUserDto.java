package com.ukhanov.realhelpdesk.integration.telegram.dto;

public class TelegramUserDto {

  // Имена полей соответствуют camelCase ключам в JavaScript payload
  private Long id;
  private Boolean is_bot;
  private String first_name;
  private String last_name;
  private String username;
  private String language_code;
  private Boolean is_premium;
  private Boolean added_to_attachment_menu;
  private Boolean allows_write_to_pm;
  private String photo_url;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Boolean getIs_bot() {
    return is_bot;
  }

  public void setIs_bot(Boolean is_bot) {
    this.is_bot = is_bot;
  }

  public String getFirst_name() {
    return first_name;
  }

  public void setFirst_name(String first_name) {
    this.first_name = first_name;
  }

  public String getLast_name() {
    return last_name;
  }

  public void setLast_name(String last_name) {
    this.last_name = last_name;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getLanguage_code() {
    return language_code;
  }

  public void setLanguage_code(String language_code) {
    this.language_code = language_code;
  }

  public Boolean getIs_premium() {
    return is_premium;
  }

  public void setIs_premium(Boolean is_premium) {
    this.is_premium = is_premium;
  }

  public Boolean getAdded_to_attachment_menu() {
    return added_to_attachment_menu;
  }

  public void setAdded_to_attachment_menu(Boolean added_to_attachment_menu) {
    this.added_to_attachment_menu = added_to_attachment_menu;
  }

  public Boolean getAllows_write_to_pm() {
    return allows_write_to_pm;
  }

  public void setAllows_write_to_pm(Boolean allows_write_to_pm) {
    this.allows_write_to_pm = allows_write_to_pm;
  }

  public String getPhoto_url() {
    return photo_url;
  }

  public void setPhoto_url(String photo_url) {
    this.photo_url = photo_url;
  }

}
