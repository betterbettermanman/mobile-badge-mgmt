package com.mobilebadge.adapt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilebadge.config.AppProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 火山引擎豆包语音 · 大模型录音文件识别（异步 submit + query）。
 * 固定开启 enable_speaker_info 与 show_utterances，并携带热词。
 */
@Component
@ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "volc")
public class VolcAsrAdapter implements AsrAdapter {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int SUCCESS = 20000000;
  private static final int PROCESSING = 20000001;
  private static final int QUEUED = 20000002;

  private final AppProperties.Volc config;
  private final List<String> secrets;
  private final HttpClient http;

  @Autowired
  public VolcAsrAdapter(AppProperties properties) {
    this(properties.getAsr().getVolc(), properties.secretValues(), HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build());
  }

  VolcAsrAdapter(AppProperties.Volc config, List<String> secrets, HttpClient http) {
    this.config = config;
    this.secrets = secrets;
    this.http = http;
  }

  @Override
  public boolean async() {
    return true;
  }

  @Override
  public AsrResult transcribe(long sessionId, String storageKey) {
    throw new VendorCallException("火山 ASR 为异步任务，不能在请求线程里同步等待", false);
  }

  @Override
  public String submit(long sessionId, String storageKey, String audioUrl, List<String> hotwords) {
    requireKeys();
    String requestId = UUID.randomUUID().toString();
    HttpResponse<String> response = send(config.getSubmitUrl(), requestId, submitBody(sessionId, storageKey, audioUrl, hotwords));
    int code = statusCode(response);
    if (code != SUCCESS) {
      throw failure(code, response.body());
    }
    return requestId;
  }

  @Override
  public AsrPoll fetch(String vendorTaskId) {
    requireKeys();
    HttpResponse<String> response = send(config.getQueryUrl(), vendorTaskId, "{}");
    int code = statusCode(response);
    if (code == PROCESSING || code == QUEUED) {
      return new AsrPoll(AsrPollState.PENDING, null, null);
    }
    if (code == SUCCESS) {
      return new AsrPoll(AsrPollState.SUCCEEDED, parseResult(response.body()), null);
    }
    throw failure(code, response.body());
  }

  static String mapSpeaker(String raw) {
    if (raw == null || raw.isBlank()) {
      return "spk0";
    }
    String trimmed = raw.trim();
    if (trimmed.regionMatches(true, 0, "spk", 0, 3)) {
      String rest = trimmed.substring(3);
      return rest.isBlank() ? "spk0" : "spk" + rest;
    }
    try {
      int number = Integer.parseInt(trimmed);
      return "spk" + Math.max(0, number - 1);
    } catch (NumberFormatException ex) {
      return "spk0";
    }
  }

  static String audioFormat(String storageKey) {
    String lower = storageKey == null ? "" : storageKey.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac")) {
      return "mp3";
    }
    if (lower.endsWith(".ogg")) {
      return "ogg";
    }
    return "wav";
  }

  private String submitBody(long sessionId, String storageKey, String audioUrl, List<String> hotwords) {
    try {
      Map<String, Object> request = new LinkedHashMap<>();
      request.put("model_name", "bigmodel");
      request.put("enable_itn", true);
      request.put("enable_punc", true);
      request.put("show_utterances", true);
      request.put("enable_speaker_info", true);
      List<String> words = hotwords == null ? List.of() : hotwords.stream().filter(word -> word != null && !word.isBlank()).toList();
      if (!words.isEmpty()) {
        List<Map<String, String>> table = new ArrayList<>();
        for (String word : words) {
          table.add(Map.of("word", word.trim()));
        }
        request.put("corpus", Map.of("context", MAPPER.writeValueAsString(Map.of("hotwords", table))));
      }
      String uid = config.getUid() == null || config.getUid().isBlank() ? "mobile-badge" : config.getUid();
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("user", Map.of("uid", uid + "-" + sessionId));
      root.put("audio", Map.of("format", audioFormat(storageKey), "url", audioUrl));
      root.put("request", request);
      return MAPPER.writeValueAsString(root);
    } catch (IOException ex) {
      throw new VendorCallException("云分析暂时失败（请求无法编码）", false);
    }
  }

  private AsrResult parseResult(String body) {
    try {
      JsonNode root = MAPPER.readTree(body == null ? "{}" : body);
      JsonNode result = root.path("result");
      String full = result.path("text").asText("");
      List<AsrSentence> sentences = new ArrayList<>();
      JsonNode utterances = result.path("utterances");
      if (utterances.isArray()) {
        for (JsonNode utterance : utterances) {
          String text = utterance.path("text").asText("").trim();
          if (text.isEmpty()) {
            continue;
          }
          String speaker = utterance.path("speaker_id").asText("");
          if (speaker.isBlank()) {
            speaker = utterance.path("speaker").asText("");
          }
          if (speaker.isBlank()) {
            speaker = utterance.path("additions").path("speaker").asText("");
          }
          sentences.add(new AsrSentence(mapSpeaker(speaker), text));
        }
      }
      if (sentences.isEmpty() && !full.isBlank()) {
        sentences.add(new AsrSentence("spk0", full.trim()));
      }
      if (full.isBlank() && sentences.isEmpty()) {
        throw new VendorCallException("云分析暂时失败（转写结果为空）", false);
      }
      if (full.isBlank()) {
        full = String.join("\n", sentences.stream().map(AsrSentence::text).toList());
      }
      return new AsrResult(full, List.copyOf(sentences));
    } catch (VendorCallException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new VendorCallException("云分析暂时失败（转写结果无法解析）", false);
    }
  }

  private HttpResponse<String> send(String url, String requestId, String body) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(20))
          .header("Content-Type", "application/json")
          .header("X-Api-App-Key", config.getAppKey())
          .header("X-Api-Access-Key", config.getAccessKey())
          .header("X-Api-Resource-Id", config.getResourceId())
          .header("X-Api-Request-Id", requestId)
          .header("X-Api-Sequence", "-1")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      return http.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new VendorCallException("云分析暂时失败（超时）", true);
    } catch (IOException ex) {
      throw new VendorCallException("云分析暂时失败（超时）", true);
    }
  }

  private void requireKeys() {
    if (config.getAppKey() == null || config.getAppKey().isBlank()
        || config.getAccessKey() == null || config.getAccessKey().isBlank()) {
      throw new VendorCallException("火山引擎 ASR 未配置，请由运维注入环境变量", false);
    }
  }

  private int statusCode(HttpResponse<String> response) {
    return response.headers().firstValue("X-Api-Status-Code")
        .or(() -> response.headers().firstValue("x-api-status-code"))
        .map(VolcAsrAdapter::parseCode)
        .orElse(response.statusCode() >= 500 ? 55000000 : 45000000);
  }

  private static int parseCode(String raw) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ex) {
      return 45000000;
    }
  }

  private VendorCallException failure(int code, String body) {
    String friendly = code >= 55000000 || code >= 500 && code < 600
        ? "云分析暂时失败（厂商 5xx）"
        : "云分析暂时失败（请求被拒绝）";
    String redacted = SecretRedactor.redact(body, secrets);
    if (redacted != null && redacted.contains(friendly)) {
      return new VendorCallException(friendly, code >= 55000000);
    }
    return new VendorCallException(friendly, code >= 55000000 || (code >= 500 && code < 600));
  }
}
