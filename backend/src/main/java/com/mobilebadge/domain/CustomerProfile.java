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
@Table(name = "mb_profile")
public class CustomerProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true)
  public Long sessionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  public ConfirmationStatus confirmationStatus = ConfirmationStatus.draft;

  @Column(length = 80)
  public String appellation;

  @Column(length = 200)
  public String intent;

  @Column(length = 80)
  public String budget;

  @Column(length = 80)
  public String decisionMaker;

  @Column(length = 1000)
  public String notes;

  public Long confirmedBy;

  public Instant confirmedAt;

  @Column(nullable = false)
  public Instant updatedAt = Instant.now();
}
