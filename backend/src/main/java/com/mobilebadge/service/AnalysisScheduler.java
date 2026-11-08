package com.mobilebadge.service;

import com.mobilebadge.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnalysisScheduler {

  private final AnalysisService analysis;
  private final WebhookDispatcher webhooks;

  public AnalysisScheduler(AnalysisService analysis, WebhookDispatcher webhooks) {
    this.analysis = analysis;
    this.webhooks = webhooks;
  }

  @Scheduled(
      fixedDelayString = "${app.analysis.poll-ms:4000}",
      initialDelayString = "${app.analysis.initial-delay-ms:4000}")
  public void pollJobs() {
    analysis.tick();
  }

  @Scheduled(
      fixedDelayString = "${app.webhook.poll-ms:15000}",
      initialDelayString = "${app.analysis.initial-delay-ms:4000}")
  public void pollWebhooks() {
    webhooks.retryDue();
  }
}
