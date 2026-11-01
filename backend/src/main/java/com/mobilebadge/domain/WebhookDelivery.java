package com.mobilebadge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "mb_webhook_delivery")
public class WebhookDelivery {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long webhookId;

  @Column(nullable = false, length = 64)
  public String eventName;

  @Column(nullable = false)
  public int statusCode;

  @Column(nullable = false, length = 300)
  public String detail;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();
}
