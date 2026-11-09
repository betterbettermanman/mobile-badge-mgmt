package com.mobilebadge.service;

import com.mobilebadge.domain.AnalysisJob;
import com.mobilebadge.repo.AnalysisJobRepository;
import com.mobilebadge.common.ApiException;
import com.mobilebadge.domain.JobStatus;
import org.springframework.stereotype.Component;

@Component
public class AnalysisGate {

  private final AnalysisJobRepository jobs;

  public AnalysisGate(AnalysisJobRepository jobs) {
    this.jobs = jobs;
  }

  public void assertSucceeded(long sessionId) {
    AnalysisJob job = jobs.findBySessionId(sessionId).orElse(null);
    if (job != null && job.status != JobStatus.succeeded) {
      throw ApiException.conflict(
          "ANALYSIS_PENDING",
          "须分析完成（succeeded）后才可确认；「生成草案中（千问）」仅可预览。");
    }
  }
}
