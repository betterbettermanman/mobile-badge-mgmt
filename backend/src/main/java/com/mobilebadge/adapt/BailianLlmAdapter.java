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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 阿里云百炼通义千问。默认 qwen3.7-flash，只解析结构化 JSON 草案。
 * 本适配器不写确认状态。
 */
@Component
@ConditionalOnProperty(prefix = "app.llm", name = "provider", havingValue = "bailian")
public class BailianLlmAdapter implements LlmAdapter {

  static final String SYSTEM_PROMPT = """
      你是门店会话分析助手。只输出一个 JSON 对象，不要 Markdown。
      结构必须是 {"profile":{"appellation":"","intent":"","budget":"","decisionMaker":"","notes":""},"needs":[{"title":"","detail":""}]}。
      所有字段都是字符串。这只是草案，禁止输出确认、confirmed 或要求自动入库。
      信息不足时用空字符串，不要编造确认结论。
      """;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AppProperties.Bailian config;
  private final List<String> secrets;
  private final HttpClient http;

  @Autowired
  public BailianLlmAdapter(AppProperties properties) {
    this(properties.getLlm().getBailian(), properties.secretValues(), HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build());
  }

  BailianLlmAdapter(AppProperties.Bailian config, List<String> secrets, HttpClient http) {
    this.config = config;
    this.secrets = secrets;
    this.http = http;
  }

  @Override
  public ProfileDraft extractProfile(String customerLabel, String transcript) {
    return extract(customerLabel, transcript).profile();
  }

  @Override
  public List<NeedDraft> extractNeeds(String transcript) {
    return extract("", transcript).needs();
  }

  @Override
  public Extraction extract(String customerLabel, String transcript) {
    requireKey();
    String model = config.getModel() == null || config.getModel().isBlank() ? "qwen3.7-flash" : config.getModel();
    String content = call(model, customerLabel, transcript);
    return DraftJson.parse(content);
  }

  private String call(String model, String customerLabel, String transcript) {
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", model);
      body.put("temperature", 0.2);
      body.put("response_format", Map.of("type", "json_object"));
      body.put("messages", List.of(
          Map.of("role", "system", "content", SYSTEM_PROMPT),
          Map.of("role", "user", "content", userContent(customerLabel, transcript))));
      HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint()))
          .timeout(Duration.ofSeconds(30))
          .header("Authorization", "Bearer " + config.getApiKey())
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 500) {
        throw new VendorCallException("云分析暂时失败（厂商 5xx）", true);
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new VendorCallException("云分析暂时失败（请求被拒绝）", false);
      }
      JsonNode root = MAPPER.readTree(response.body());
      String content = root.path("choices").path(0).path("message").path("content").asText("");
      if (content.isBlank()) {
        throw new VendorCallException("模型未返回内容", false);
      }
      return content;
    } catch (VendorCallException ex) {
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new VendorCallException("云分析暂时失败（超时）", true);
    } catch (java.net.http.HttpTimeoutException ex) {
      throw new VendorCallException("云分析暂时失败（超时）", true);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new VendorCallException("模型未返回可解析的 JSON", false);
    } catch (IOException ex) {
      throw new VendorCallException("云分析暂时失败（无法连接模型服务）", true);
    }
  }

  private String endpoint() {
    String base = config.getBaseUrl() == null ? "" : config.getBaseUrl().trim();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + "/chat/completions";
  }

  private void requireKey() {
    if (config.getApiKey() == null || config.getApiKey().isBlank()) {
      throw new VendorCallException("阿里云百炼 LLM 未配置，请由运维注入环境变量", false);
    }
  }

  private static String userContent(String customerLabel, String transcript) {
    String label = customerLabel == null ? "" : customerLabel;
    String text = transcript == null ? "" : transcript;
    return "客户备注：" + label + "\n转写：\n" + text;
  }
}
