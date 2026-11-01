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
import java.time.LocalDate;

@Entity
@Table(name = "mb_export_job")
public class ExportJob {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long organizationId;

  @Column(nullable = false)
  public Long createdBy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public ExportFormat format;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  public JobStatus status = JobStatus.queued;

  public LocalDate fromDate;

  public LocalDate toDate;

  @Column(nullable = false)
  public boolean includeConfirmedProfiles = true;

  @Column(nullable = false)
  public boolean includeConfirmedNeeds = true;

  @Column(nullable = false)
  public boolean includeSessionMeta;

  @Column(nullable = false)
  public boolean includeTranscript;

  @Column(length = 300)
  public String storageKey;

  @Column(length = 300)
  public String message;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();
}
