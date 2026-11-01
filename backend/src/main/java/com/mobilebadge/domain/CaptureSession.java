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
@Table(name = "mb_capture_session")
public class CaptureSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long organizationId;

  public Long storeId;

  @Column(nullable = false)
  public Long ownerUserId;

  @Column(length = 80)
  public String customerLabel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  public DeviceSource source;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  public CaptureStatus captureStatus = CaptureStatus.draft;

  @Enumerated(EnumType.STRING)
  @Column(length = 32)
  public TranscriptStatus transcriptStatus;

  @Column(length = 300)
  public String failureReason;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();

  @Column(nullable = false)
  public Instant updatedAt = Instant.now();
}
