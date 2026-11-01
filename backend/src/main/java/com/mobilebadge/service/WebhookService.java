package com.mobilebadge.service;

import com.mobilebadge.adapt.WebhookEvents;
import com.mobilebadge.common.ApiException;
import com.mobilebadge.domain.WebhookDelivery;
import com.mobilebadge.domain.WebhookEndpoint;
import com.mobilebadge.repo.WebhookDeliveryRepository;
import com.mobilebadge.repo.WebhookEndpointRepository;
import com.mobilebadge.security.CurrentUser;
import com.mobilebadge.web.ApiModels.WebhookCreate;
import com.mobilebadge.web.ApiModels.WebhookDeliveryResponse;
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

  public WebhookService(WebhookEndpointRepository endpoints, WebhookDeliveryRepository deliveries) {
    this.endpoints = endpoints;
    this.deliveries = deliveries;
  }

  public List<WebhookResponse> list() {
    Long orgId = CurrentUser.require().organizationId();
    return endpoints.findByOrganizationIdOrderByIdAsc(orgId).stream().map(this::toResponse).toList();
  }

  @Transactional
  public WebhookResponse create(WebhookCreate request) {
    assertHttps(request.url());
    assertEvents(request.events());
    WebhookEndpoint endpoint = new WebhookEndpoint();
    endpoint.organizationId = CurrentUser.require().organizationId();
    endpoint.url = request.url().trim();
    endpoint.secret = request.secret().trim();
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
      endpoint.url = request.url().trim();
    }
    if (request.secret() != null && !request.secret().isBlank()) {
      if (request.secret().trim().length() < 8) {
        throw ApiException.badRequest("SECRET", "密钥至少 8 位");
      }
      endpoint.secret = request.secret().trim();
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
    WebhookDelivery delivery = new WebhookDelivery();
    delivery.webhookId = endpoint.id;
    delivery.eventName = "webhook.test";
    delivery.statusCode = 0;
    delivery.detail = "已记录测试事件（一期未向外部 URL 投递）";
    deliveries.save(delivery);
    return toDelivery(delivery);
  }

  public List<WebhookDeliveryResponse> deliveries() {
    Long orgId = CurrentUser.require().organizationId();
    List<Long> ids = endpoints.findByOrganizationIdOrderByIdAsc(orgId).stream().map(item -> item.id).toList();
    if (ids.isEmpty()) {
      return List.of();
    }
    return deliveries.findByWebhookIdInOrderByIdDesc(ids).stream().limit(50).map(this::toDelivery).toList();
  }

  private WebhookEndpoint require(Long id) {
    WebhookEndpoint endpoint = endpoints.findById(id).orElseThrow(ApiException::notFound);
    if (!endpoint.organizationId.equals(CurrentUser.require().organizationId())) {
      throw ApiException.forbidden();
    }
    return endpoint;
  }

  private WebhookResponse toResponse(WebhookEndpoint endpoint) {
    return new WebhookResponse(
        endpoint.id,
        endpoint.url,
        mask(endpoint.secret),
        Arrays.asList(endpoint.events.split(",")),
        endpoint.enabled,
        endpoint.createdAt);
  }

  private WebhookDeliveryResponse toDelivery(WebhookDelivery delivery) {
    return new WebhookDeliveryResponse(
        delivery.id, delivery.webhookId, delivery.eventName, delivery.statusCode, delivery.detail, delivery.createdAt);
  }

  private static void assertHttps(String url) {
    try {
      URI uri = URI.create(url.trim());
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

  private static String mask(String secret) {
    if (secret == null || secret.length() < 2) {
      return "****";
    }
    return "****" + secret.substring(secret.length() - 2);
  }
}
