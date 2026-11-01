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
@Table(name = "mb_consent_record")
public class ConsentRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long organizationId;

  @Column(nullable = false)
  public Long userId;

  public Long sessionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  public ConsentState status = ConsentState.valid;

  @Column(nullable = false, length = 200)
  public String statement;

  @Column(nullable = false)
  public Instant grantedAt = Instant.now();

  @Column(nullable = false)
  public Instant expiresAt;

  public Instant revokedAt;
}
