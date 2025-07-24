package com.ukhanov.realhelpdesk.core.mail.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "unsubscribed_emails")
public class UnsubscribedEmail {
  @Id
  @GeneratedValue (strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true)
  private String email;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private NotificationEvent muteEvent = NotificationEvent.NONE;

  public UnsubscribedEmail() {}

  @Column(nullable = false, updatable = false)
  private Instant inStopListAt;


  public UnsubscribedEmail(String email, NotificationEvent muteEvent) {
    this.email = email;
    this.muteEvent = muteEvent;
  }

  public String getEmail() {
    return email;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getId() {
    return id;
  }

  public NotificationEvent getMuteEvent() {
    return muteEvent;
  }

  public void setMuteEvent(NotificationEvent muteEvent) {
    this.muteEvent = muteEvent;
  }

  public void setInStopListAt(Instant inStopListAt) {
    this.inStopListAt = inStopListAt;
  }

  public void setInStopListAt() {
    this.inStopListAt = Instant.now();
  }

}
