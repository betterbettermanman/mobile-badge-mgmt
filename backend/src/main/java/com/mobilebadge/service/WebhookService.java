package com.mobilebadge.service;

import com.mobilebadge.adapt.SecretBox;
import com.mobilebadge.adapt.WebhookEvents;
import com.mobilebadge.adapt.WebhookPolicyRules;
import com.mobilebadge.common.ApiException;
import com.mobilebadge.domain.WebhookDelivery;
import com.mobilebadge.domain.WebhookEndpoint;
import com.mobilebadge.domain.WebhookPolicy;
import com.mobilebadge.repo.WebhookDeliveryRepository;
import com.mobilebadge.repo.WebhookEndpointRepository;
import com.mobilebadge.repo.WebhookPolicyRepository;
import com.mobilebadge.security.CurrentUser;
import com.mobilebadge.web.ApiModels.WebhookCreate;
import com.mobilebadge.web.ApiModels.WebhookDeliveryResponse;
import com.mobilebadge.web.ApiModels.WebhookPolicyResponse;
import com.mobilebadge.web.ApiModels.WebhookPolicyUpdate;
import com.mobilebadge.web.ApiModels.WebhookResponse;
import com.mobilebadge.web.ApiModels.WebhookUpdate;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

  private final WebhookEndpointRepository endpoints;
  private final WebhookDeliveryRepository deliveries;
  private final WebhookPolicyRepository policies;
  private final WebhookDispatcher dispatcher;
  private final SecretBox secrets;

  public WebhookService(
      WebhookEndpointRepository endpoints,
      WebhookDeliveryRepository deliveries,
      WebhookPolicyRepository policies,
      WebhookDispatcher dispatcher,
      SecretBox secrets) {
    this.endpoints = endpoints;
    this.deliveries = deliveries;
    this.policies = policies;
    this.dispatcher = dispatcher;
    this.secrets = secrets;
  }

  public WebhookPolicyResponse policy() {
    return toPolicy(loadPolicy());
  }

  @Transactional
  public WebhookPolicyResponse updatePolicy(WebhookPolicyUpdate request) {
    WebhookPolicy policy = loadPolicy();
    if (request.allowlist() != null) {
      policy.allowlist = request.allowlist().trim();
    }
    if (request.outboundEnabled() != null) {
      policy.outboundEnabled = request.outboundEnabled();
    }
    policies.save(policy);
    return toPolicy(policy);
  }

  public List<WebhookResponse> list() {
    Long orgId = CurrentUser.require().organizationId();
    return endpoints.findByOrganizationIdOrderByIdAsc(orgId).stream().map(this::toResponse).toList();
  }

  @Transactional
  public WebhookResponse create(WebhookCreate request) {
    assertHttps(request.url());
    assertEvents(request.events());
    assertAllowlist(request.url());
    WebhookEndpoint endpoint = new WebhookEndpoint();
    endpoint.organizationId = CurrentUser.require().organizationId();
    endpoint.url = request.url().trim();
    endpoint.secret = secrets.seal(request.secret().trim());
    endpoint.events = String.join(",", request.events());
    endpoint.enabled = request.enabled() == null || request.enabled();
    endpoints.save(endpoint);
    return toResponse(endpoint);
  }

  @Transactional
  public WebhookResponse update(Long id, WebhookUpdate request) {
    WebhookEndpoint endpoint = require(id);
    if (request.url() != null && !request.url().isBlank()) {
      assertHttps(request.url());
      assertAllowlist(request.url());
      endpoint.url = request.url().trim();
    }
    if (request.secret() != null && !request.secret().isBlank()) {
      if (request.secret().trim().length() < 8) {
        throw ApiException.badRequest("SECRET", "密钥至少 8 位");
      }
      endpoint.secret = secrets.seal(request.secret().trim());
    }
    if (request.events() != null) {
      assertEvents(request.events());
      endpoint.events = String.join(",", request.events());
    }
    if (request.enabled() != null) {
      endpoint.enabled = request.enabled();
    }
    endpoints.save(endpoint);
    return toResponse(endpoint);
  }

  @Transactional
  public WebhookDeliveryResponse test(Long id) {
    WebhookEndpoint endpoint = require(id);
    return toDelivery(dispatcher.deliver(endpoint, "webhook.test", "测试事件"));
  }

  public List<WebhookDeliveryResponse> deliveries() {
    Long orgId = CurrentUser.require().organizationId();
    List<Long> ids = endpoints.findByOrganizationIdOrderByIdAsc(orgId).stream().map(item -> item.id).toList();
    if (ids.isEmpty()) {
      return List.of();
    }
    return deliveries.findByWebhookIdInOrderByIdDesc(ids).stream().limit(50).map(this::toDelivery).toList();
  }

  private WebhookPolicy loadPolicy() {
    Long orgId = CurrentUser.require().organizationId();
    return policies.findByOrganizationId(orgId).orElseGet(() -> {
      WebhookPolicy created = new WebhookPolicy();
      created.organizationId = orgId;
      created.allowlist = "";
      created.outboundEnabled = false;
      return created;
    });
  }

  private WebhookEndpoint require(Long id) {
    WebhookEndpoint endpoint = endpoints.findById(id).orElseThrow(ApiException::notFound);
    if (!endpoint.organizationId.equals(CurrentUser.require().organizationId())) {
      throw ApiException.forbidden();
    }
    return endpoint;
  }

  private WebhookResponse toResponse(WebhookEndpoint endpoint) {
    WebhookPolicy policy = policies.findByOrganizationId(endpoint.organizationId).orElse(null);
    String allowlist = policy == null ? "" : policy.allowlist;
    String notice = allowlist == null || allowlist.isBlank()
        ? "已保存配置。注意：白名单为空，运行时仍不会出站。"
        : "已保存。密钥已脱敏回显，明文不会再次展示。";
    return new WebhookResponse(
        endpoint.id,
        endpoint.url,
        mask(endpoint.secret),
        Arrays.asList(endpoint.events.split(",")),
        endpoint.enabled,
        endpoint.createdAt,
        notice);
  }

  private WebhookPolicyResponse toPolicy(WebhookPolicy policy) {
    String allowlist = policy.allowlist == null ? "" : policy.allowlist;
    String hint = allowlist.isBlank()
        ? "当前白名单为空：即使启用也不会向外部投递。"
        : "仅白名单内的 HTTPS 主机可以出站。";
    return new WebhookPolicyResponse(allowlist, policy.outboundEnabled, hint);
  }

  private WebhookDeliveryResponse toDelivery(WebhookDelivery delivery) {
    return new WebhookDeliveryResponse(
        delivery.id,
        delivery.webhookId,
        delivery.eventName,
        delivery.statusCode,
        delivery.detail,
        delivery.createdAt,
        delivery.state == null ? "logged" : delivery.state,
        delivery.attempt,
        delivery.nextRetryAt);
  }

  private void assertAllowlist(String url) {
    WebhookPolicy policy = policies.findByOrganizationId(CurrentUser.require().organizationId()).orElse(null);
    String allowlist = policy == null ? "" : policy.allowlist;
    if (allowlist == null || allowlist.isBlank()) {
      return;
    }
    if (!WebhookPolicyRules.hostAllowed(url, allowlist)) {
      throw ApiException.badRequest("WEBHOOK_ALLOWLIST", "该 URL 域名不在白名单内，请先加入白名单或更换 URL。");
    }
  }

  private static void assertHttps(String url) {
    String trimmed = url == null ? "" : url.trim();
    if (trimmed.toLowerCase().startsWith("http://")) {
      throw ApiException.badRequest("WEBHOOK_URL", "禁止使用 http://，请改用 HTTPS。");
    }
    try {
      URI uri = URI.create(trimmed);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
        throw ApiException.badRequest("WEBHOOK_URL", "请输入合法的 HTTPS URL");
      }
    } catch (IllegalArgumentException ex) {
      throw ApiException.badRequest("WEBHOOK_URL", "请输入合法的 HTTPS URL");
    }
  }

  private static void assertEvents(List<String> events) {
    if (events == null || events.isEmpty()) {
      throw ApiException.badRequest("WEBHOOK_EVENT", "请选择订阅事件");
    }
    for (String event : events) {
      if (!WebhookEvents.ALLOWED.contains(event)) {
        throw ApiException.badRequest("WEBHOOK_EVENT", "仅支持 profile.confirmed、need.confirmed、transcript.succeeded");
      }
    }
  }

  private String mask(String stored) {
    String secret;
    try {
      secret = secrets.open(stored);
    } catch (RuntimeException ex) {
      return "****";
    }
    if (secret == null || secret.length() < 2) {
      return "****";
    }
    return "****" + secret.substring(secret.length() - 2);
  }
}
