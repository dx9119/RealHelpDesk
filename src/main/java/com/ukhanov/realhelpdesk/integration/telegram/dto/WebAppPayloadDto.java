package com.ukhanov.realhelpdesk.integration.telegram.dto;

public class WebAppPayloadDto {

  // Имена полей соответствуют camelCase ключам в JavaScript payload
  private String initData;
  private String queryId;
  private TelegramUserDto user;
  private TelegramChatDto chat;
  private TelegramUserDto receiver;
  private String startParam;
  private Long authDate;
  private String hash;
  private String platform;
  private String version;
  private String colorScheme;

  @Override
  public String toString() {
    return "WebAppPayloadDto{" +
        "initData='" + initData + '\'' +
        ", queryId='" + queryId + '\'' +
        ", user=" + user +
        ", chat=" + chat +
        ", receiver=" + receiver +
        ", startParam='" + startParam + '\'' +
        ", authDate=" + authDate +
        ", hash='" + hash + '\'' +
        ", platform='" + platform + '\'' +
        ", version='" + version + '\'' +
        ", colorScheme='" + colorScheme + '\'' +
        ", themeParams=" + themeParams +
        '}';
  }

  public String getInitData() {
    return initData;
  }

  public void setInitData(String initData) {
    this.initData = initData;
  }

  public String getQueryId() {
    return queryId;
  }

  public void setQueryId(String queryId) {
    this.queryId = queryId;
  }

  public TelegramUserDto getUser() {
    return user;
  }

  public void setUser(TelegramUserDto user) {
    this.user = user;
  }

  public TelegramChatDto getChat() {
    return chat;
  }

  public void setChat(TelegramChatDto chat) {
    this.chat = chat;
  }

  public TelegramUserDto getReceiver() {
    return receiver;
  }

  public void setReceiver(TelegramUserDto receiver) {
    this.receiver = receiver;
  }

  public String getStartParam() {
    return startParam;
  }

  public void setStartParam(String startParam) {
    this.startParam = startParam;
  }

  public Long getAuthDate() {
    return authDate;
  }

  public void setAuthDate(Long authDate) {
    this.authDate = authDate;
  }

  public String getHash() {
    return hash;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getColorScheme() {
    return colorScheme;
  }

  public void setColorScheme(String colorScheme) {
    this.colorScheme = colorScheme;
  }

  public Object getThemeParams() {
    return themeParams;
  }

  public void setThemeParams(Object themeParams) {
    this.themeParams = themeParams;
  }

  private Object themeParams;

}
