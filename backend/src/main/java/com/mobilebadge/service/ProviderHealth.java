package com.mobilebadge.service;

import com.mobilebadge.config.AppProperties;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProviderHealth {

  private final AppProperties properties;

  public ProviderHealth(AppProperties properties) {
    this.properties = properties;
  }

  public Map<String, String> snapshot() {
    String asr = properties.getAsr().getProvider();
    String llm = properties.getLlm().getProvider();
    boolean asrReady = "mock".equalsIgnoreCase(asr) || configured(properties.getAsr().getVolc().getAppKey(), properties.getAsr().getVolc().getAccessKey());
    boolean llmReady = "mock".equalsIgnoreCase(llm) || configured(properties.getLlm().getBailian().getApiKey());
    return Map.of(
        "status", "ok",
        "service", "mobile-badge-mgmt",
        "asrProvider", asr == null ? "mock" : asr,
        "llmProvider", llm == null ? "mock" : llm,
        "asrConfigured", Boolean.toString(asrReady),
        "llmConfigured", Boolean.toString(llmReady));
  }

  private static boolean configured(String... values) {
    for (String value : values) {
      if (value == null || value.isBlank()) {
        return false;
      }
    }
    return true;
  }
}
