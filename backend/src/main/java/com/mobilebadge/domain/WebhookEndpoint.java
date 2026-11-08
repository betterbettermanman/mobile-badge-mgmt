package com.mobilebadge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "mb_webhook")
public class WebhookEndpoint {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long organizationId;

  @Column(nullable = false, length = 300)
  public String url;

  @Column(nullable = false, length = 500)
  public String secret;

  @Column(nullable = false, length = 200)
  public String events;

  @Column(nullable = false)
  public boolean enabled = true;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();
}
