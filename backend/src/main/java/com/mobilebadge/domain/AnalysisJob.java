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
@Table(name = "mb_analysis_job")
public class AnalysisJob {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true)
  public Long sessionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  public JobStatus status = JobStatus.queued;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  public AnalysisStage stage = AnalysisStage.none;

  @Column(nullable = false, length = 32)
  public String vendor = "mock";

  @Column(length = 80)
  public String vendorTaskId;

  /** 已开始的执行次数（含首次）。 */
  @Column(nullable = false)
  public int attempt;

  /** 顾问手动「重试分析」次数。上限 3，不可配置。 */
  @Column(nullable = false)
  public int retryCount;

  @Column(length = 300)
  public String lastError;

  @Column(length = 20000)
  public String transcriptJson;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();

  @Column(nullable = false)
  public Instant updatedAt = Instant.now();
}
