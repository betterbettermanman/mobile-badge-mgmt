package com.mobilebadge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "mb_need")
public class NeedItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long sessionId;

  @Column(nullable = false, length = 120)
  public String title;

  @Column(length = 500)
  public String detail;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  public ConfirmationStatus confirmationStatus = ConfirmationStatus.draft;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  public NeedFollowStatus followStatus = NeedFollowStatus.open;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  public NeedOrigin origin = NeedOrigin.manual;

  @Column(nullable = false)
  public Long ownerUserId;

  public Long confirmedBy;

  public Instant confirmedAt;

  @Column(nullable = false)
  public Instant updatedAt = Instant.now();
}
