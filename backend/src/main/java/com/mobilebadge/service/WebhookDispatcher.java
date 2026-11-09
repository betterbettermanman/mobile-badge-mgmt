package com.mobilebadge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilebadge.adapt.HostLookup;
import com.mobilebadge.adapt.SecretBox;
import com.mobilebadge.adapt.SecretRedactor;
import com.mobilebadge.adapt.WebhookEvents;
import com.mobilebadge.adapt.WebhookPolicyRules;
import com.mobilebadge.adapt.WebhookPolicyRules.Block;
import com.mobilebadge.adapt.WebhookPoster;
import com.mobilebadge.config.AppProperties;
import com.mobilebadge.domain.WebhookDelivery;
import com.mobilebadge.domain.WebhookEndpoint;
import com.mobilebadge.domain.WebhookPolicy;
import com.mobilebadge.repo.WebhookDeliveryRepository;
import com.mobilebadge.repo.WebhookEndpointRepository;
import com.mobilebadge.repo.WebhookPolicyRepository;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookDispatcher {

  private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

  private final WebhookEndpointRepository endpoints;
  private final WebhookDeliveryRepository deliveries;
  private final WebhookPolicyRepository policies;
  private final WebhookPoster poster;
  private final HostLookup hosts;
  private final SecretBox secrets;
  private final AppProperties properties;
  private final ObjectMapper objectMapper;

  public WebhookDispatcher(
      WebhookEndpointRepository endpoints,
      WebhookDeliveryRepository deliveries,
      WebhookPolicyRepository policies,
      WebhookPoster poster,
      HostLookup hosts,
      SecretBox secrets,
      AppProperties properties,
      ObjectMapper objectMapper) {
    this.endpoints = endpoints;
    this.deliveries = deliveries;
    this.policies = policies;
    this.poster = poster;
    this.hosts = hosts;
    this.secrets = secrets;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public void dispatch(Long organizationId, String event, String detail) {
    if (WebhookEvents.TRANSCRIPT_SUCCEEDED.equals(event) && !properties.getWebhook().isAllowTranscriptEvent()) {
      return;
    }
    try {
      List<WebhookEndpoint> hooks = endpoints.findByOrganizationIdAndEnabled(organizationId, true);
      for (WebhookEndpoint hook : hooks) {
        boolean subscribed = Arrays.stream(hook.events.split(",")).map(String::trim).anyMatch(event::equals);
        if (!subscribed) {
          continue;
        }
        deliver(hook, event, detail);
      }
    } catch (RuntimeException ex) {
      log.warn("webhook dispatch failed: {}", SecretRedactor.redact(ex.getMessage(), properties.secretValues()));
    }
  }

  @Transactional
  public WebhookDelivery deliver(WebhookEndpoint endpoint, String event, String detail) {
    WebhookPolicy policy = policies.findByOrganizationId(endpoint.organizationId).orElse(null);
    String allowlist = policy == null ? "" : policy.allowlist;
    boolean outbound = policy != null && policy.outboundEnabled;
    Block block = WebhookPolicyRules.evaluate(endpoint.url, allowlist, outbound, false);
    if (block == Block.NONE && hosts.isPrivateOrLocal(URI.create(endpoint.url.trim()).getHost())) {
      block = Block.PRIVATE_TARGET;
    }
    WebhookDelivery delivery = new WebhookDelivery();
    delivery.webhookId = endpoint.id;
    delivery.eventName = event;
    delivery.requestBody = body(event, detail);
    delivery.createdAt = Instant.now();
    if (block != Block.NONE) {
      delivery.state = "skipped";
      delivery.statusCode = 0;
      delivery.attempt = 0;
      String skip = WebhookPolicyRules.skipDetail(block);
      delivery.detail = "webhook.test".equals(event) ? "已记录测试事件（" + skip + "）" : skip;
      return deliveries.save(delivery);
    }
    attemptSend(endpoint, delivery);
    return delivery;
  }

  @Transactional
  public void retryDue() {
    Instant now = Instant.now();
    for (WebhookDelivery delivery : deliveries.findByStateAndNextRetryAtLessThanEqual("retrying", now)) {
      endpoints.findById(delivery.webhookId).ifPresent(endpoint -> attemptSend(endpoint, delivery));
    }
  }

  private void attemptSend(WebhookEndpoint endpoint, WebhookDelivery delivery) {
    String secret;
    try {
      secret = secrets.open(endpoint.secret);
    } catch (RuntimeException ex) {
      delivery.state = "failed";
      delivery.statusCode = 0;
      delivery.detail = "签名密钥不可用，未向外部 URL 投递";
      deliveries.save(delivery);
      return;
    }
    String timestamp = String.valueOf(Instant.now().getEpochSecond());
    String payload = delivery.requestBody == null ? "" : delivery.requestBody;
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("X-MB-Signature", WebhookPolicyRules.signatureHeader(secret, timestamp, payload));
    headers.put("X-MB-Event", delivery.eventName);
    try {
      int code = poster.post(
          URI.create(endpoint.url.trim()),
          payload,
          headers,
          properties.getWebhook().getConnectTimeoutMs(),
          properties.getWebhook().getReadTimeoutMs());
      delivery.attempt += 1;
      delivery.statusCode = code;
      if (code >= 200 && code < 300) {
        delivery.state = "succeeded";
        delivery.nextRetryAt = null;
        delivery.detail = "已投递 HTTP " + code;
      } else {
        scheduleRetry(delivery);
      }
    } catch (IOException ex) {
      delivery.attempt += 1;
      delivery.statusCode = 0;
      scheduleRetry(delivery);
    }
    deliveries.save(delivery);
  }

  private void scheduleRetry(WebhookDelivery delivery) {
    Instant next = WebhookPolicyRules.nextRetry(
        delivery.attempt,
        properties.webhookRetryDelays(),
        Instant.now(),
        properties.getWebhook().getMaxAttempts());
    if (next == null) {
      delivery.state = "failed";
      delivery.nextRetryAt = null;
      delivery.detail = "投递失败，已达自动重试上限";
      return;
    }
    delivery.state = "retrying";
    delivery.nextRetryAt = next;
    delivery.detail = "投递失败，将自动重试（第 " + delivery.attempt + " 次）";
  }

  private String body(String event, String detail) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "event", event,
          "detail", detail == null ? "" : detail,
          "occurredAt", Instant.now().toString()));
    } catch (JsonProcessingException ex) {
      return "{\"event\":\"" + event + "\"}";
    }
  }
}
