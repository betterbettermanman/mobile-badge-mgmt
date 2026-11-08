package com.mobilebadge.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

  private final Storage storage = new Storage();
  private final Seed seed = new Seed();
  private final Asr asr = new Asr();
  private final Llm llm = new Llm();
  private final Analysis analysis = new Analysis();
  private final Webhook webhook = new Webhook();
  private String publicBaseUrl = "http://localhost:8092";
  private int audioTokenTtlSeconds = 900;

  public Storage getStorage() {
    return storage;
  }

  public Seed getSeed() {
    return seed;
  }

  public Asr getAsr() {
    return asr;
  }

  public Llm getLlm() {
    return llm;
  }

  public Analysis getAnalysis() {
    return analysis;
  }

  public Webhook getWebhook() {
    return webhook;
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl = publicBaseUrl;
  }

  public int getAudioTokenTtlSeconds() {
    return audioTokenTtlSeconds;
  }

  public void setAudioTokenTtlSeconds(int audioTokenTtlSeconds) {
    this.audioTokenTtlSeconds = audioTokenTtlSeconds;
  }

  public boolean inlineAnalysis() {
    String mode = analysis.getInline();
    if ("true".equalsIgnoreCase(mode)) {
      return true;
    }
    if ("false".equalsIgnoreCase(mode)) {
      return false;
    }
    return "mock".equalsIgnoreCase(asr.getProvider()) && "mock".equalsIgnoreCase(llm.getProvider());
  }

  public List<String> hotwords() {
    return splitCsv(asr.getVolc().getHotwords());
  }

  public int[] webhookRetryDelays() {
    List<String> parts = splitCsv(webhook.getRetryDelaysSeconds());
    if (parts.isEmpty()) {
      return new int[] {60, 300, 1800};
    }
    int[] delays = new int[parts.size()];
    for (int i = 0; i < parts.size(); i++) {
      delays[i] = Integer.parseInt(parts.get(i));
    }
    return delays;
  }

  public List<String> secretValues() {
    List<String> values = new ArrayList<>();
    addSecret(values, asr.getVolc().getAppKey());
    addSecret(values, asr.getVolc().getAccessKey());
    addSecret(values, asr.getVolc().getCallbackSecret());
    addSecret(values, llm.getBailian().getApiKey());
    addSecret(values, webhook.getDataKey());
    return values;
  }

  private static void addSecret(List<String> values, String secret) {
    if (secret != null && secret.length() >= 4) {
      values.add(secret);
    }
  }

  private static List<String> splitCsv(String raw) {
    List<String> parts = new ArrayList<>();
    if (raw == null || raw.isBlank()) {
      return parts;
    }
    for (String item : raw.split("[,，]")) {
      String trimmed = item.trim();
      if (!trimmed.isEmpty()) {
        parts.add(trimmed);
      }
    }
    return parts;
  }

  public static class Storage {
    private String root = "./data/objects";

    public String getRoot() {
      return root;
    }

    public void setRoot(String root) {
      this.root = root;
    }
  }

  public static class Seed {
    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  public static class Asr {
    private String provider = "mock";
    private final Volc volc = new Volc();

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public Volc getVolc() {
      return volc;
    }
  }

  public static class Volc {
    private String appKey = "";
    private String accessKey = "";
    private String resourceId = "volc.bigasr.auc";
    private String submitUrl = "https://openspeech.bytedance.com/api/v3/auc/bigmodel/submit";
    private String queryUrl = "https://openspeech.bytedance.com/api/v3/auc/bigmodel/query";
    private String hotwords = "";
    private String callbackSecret = "";
    private String uid = "mobile-badge";

    public String getAppKey() {
      return appKey;
    }

    public void setAppKey(String appKey) {
      this.appKey = appKey;
    }

    public String getAccessKey() {
      return accessKey;
    }

    public void setAccessKey(String accessKey) {
      this.accessKey = accessKey;
    }

    public String getResourceId() {
      return resourceId;
    }

    public void setResourceId(String resourceId) {
      this.resourceId = resourceId;
    }

    public String getSubmitUrl() {
      return submitUrl;
    }

    public void setSubmitUrl(String submitUrl) {
      this.submitUrl = submitUrl;
    }

    public String getQueryUrl() {
      return queryUrl;
    }

    public void setQueryUrl(String queryUrl) {
      this.queryUrl = queryUrl;
    }

    public String getHotwords() {
      return hotwords;
    }

    public void setHotwords(String hotwords) {
      this.hotwords = hotwords;
    }

    public String getCallbackSecret() {
      return callbackSecret;
    }

    public void setCallbackSecret(String callbackSecret) {
      this.callbackSecret = callbackSecret;
    }

    public String getUid() {
      return uid;
    }

    public void setUid(String uid) {
      this.uid = uid;
    }
  }

  public static class Llm {
    private String provider = "mock";
    private final Bailian bailian = new Bailian();

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public Bailian getBailian() {
      return bailian;
    }
  }

  public static class Bailian {
    private String apiKey = "";
    private String model = "qwen3.7-flash";
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }
  }

  public static class Analysis {
    private String inline = "auto";
    private long pollMs = 4000;
    private long initialDelayMs = 4000;

    public String getInline() {
      return inline;
    }

    public void setInline(String inline) {
      this.inline = inline;
    }

    public long getPollMs() {
      return pollMs;
    }

    public void setPollMs(long pollMs) {
      this.pollMs = pollMs;
    }

    public long getInitialDelayMs() {
      return initialDelayMs;
    }

    public void setInitialDelayMs(long initialDelayMs) {
      this.initialDelayMs = initialDelayMs;
    }
  }

  public static class Webhook {
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private boolean allowPrivate;
    private String retryDelaysSeconds = "60,300,1800";
    private int maxAttempts = 4;
    private long pollMs = 15000;
    private String dataKey = "";
    private boolean allowTranscriptEvent;

    public int getConnectTimeoutMs() {
      return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
      this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
      return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
      this.readTimeoutMs = readTimeoutMs;
    }

    public boolean isAllowPrivate() {
      return allowPrivate;
    }

    public void setAllowPrivate(boolean allowPrivate) {
      this.allowPrivate = allowPrivate;
    }

    public String getRetryDelaysSeconds() {
      return retryDelaysSeconds;
    }

    public void setRetryDelaysSeconds(String retryDelaysSeconds) {
      this.retryDelaysSeconds = retryDelaysSeconds;
    }

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public long getPollMs() {
      return pollMs;
    }

    public void setPollMs(long pollMs) {
      this.pollMs = pollMs;
    }

    public String getDataKey() {
      return dataKey;
    }

    public void setDataKey(String dataKey) {
      this.dataKey = dataKey;
    }

    public boolean isAllowTranscriptEvent() {
      return allowTranscriptEvent;
    }

    public void setAllowTranscriptEvent(boolean allowTranscriptEvent) {
      this.allowTranscriptEvent = allowTranscriptEvent;
    }
  }
}
