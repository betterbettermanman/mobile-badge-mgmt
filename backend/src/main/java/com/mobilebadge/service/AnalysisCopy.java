package com.mobilebadge.service;

import com.mobilebadge.domain.AnalysisJob;
import com.mobilebadge.domain.AnalysisStage;
import com.mobilebadge.domain.JobStatus;

public final class AnalysisCopy {

  public static final int RETRY_LIMIT = 3;

  private AnalysisCopy() {
  }

  public static boolean canRetry(AnalysisJob job) {
    return job != null && job.status == JobStatus.failed && job.retryCount < RETRY_LIMIT;
  }

  public static boolean canConfirm(AnalysisJob job) {
    return job != null && job.status == JobStatus.succeeded;
  }

  public static String uiStatus(AnalysisJob job) {
    if (job == null || job.status == null) {
      return null;
    }
    return switch (job.status) {
      case queued -> "queued";
      case processing -> job.stage == AnalysisStage.llm ? "processing_llm" : "processing_asr";
      case succeeded -> "succeeded";
      case failed -> "failed";
    };
  }

  public static String title(String uiStatus) {
    if (uiStatus == null) {
      return null;
    }
    return switch (uiStatus) {
      case "queued" -> "排队中";
      case "processing_asr" -> "分析中 · 转写中（火山）";
      case "processing_llm" -> "分析中 · 生成草案中（千问）";
      case "succeeded" -> "分析完成";
      case "failed" -> "分析失败";
      default -> uiStatus;
    };
  }

  public static String subtitle(String uiStatus) {
    if (uiStatus == null) {
      return null;
    }
    return switch (uiStatus) {
      case "queued" -> "已创建分析任务，等待处理";
      case "processing_asr" -> "火山引擎豆包 ASR 处理中（说话人分离 + 热词）";
      case "processing_llm" -> "通义千问（阿里云百炼）抽取画像/需求草案";
      case "succeeded" -> "火山转写与千问草案已就绪，请人工确认后写入正式库";
      case "failed" -> "云分析暂时失败，可重试分析";
      default -> "";
    };
  }
}
