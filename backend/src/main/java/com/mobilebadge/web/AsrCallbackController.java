package com.mobilebadge.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilebadge.service.AnalysisService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AsrCallbackController {

  private final AnalysisService analysis;
  private final ObjectMapper objectMapper;

  public AsrCallbackController(AnalysisService analysis, ObjectMapper objectMapper) {
    this.analysis = analysis;
    this.objectMapper = objectMapper;
  }

  @PostMapping(value = "/api/internal/asr/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> callback(
      @RequestHeader(value = "X-Callback-Secret", required = false) String secret,
      @RequestHeader(value = "X-Api-Request-Id", required = false) String requestId,
      @RequestBody(required = false) String body) {
    analysis.onCallback(secret, firstNonBlank(requestId, readTaskId(body)));
    return Map.of("status", "ok");
  }

  private String readTaskId(String body) {
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      JsonNode node = objectMapper.readTree(body);
      for (String field : new String[] {"request_id", "requestId", "task_id", "taskId"}) {
        String value = node.path(field).asText("");
        if (!value.isBlank()) {
          return value;
        }
      }
    } catch (Exception ignored) {
      return null;
    }
    return null;
  }

  private static String firstNonBlank(String left, String right) {
    if (left != null && !left.isBlank()) {
      return left;
    }
    return right;
  }
}
