package com.mobilebadge.service;

import com.mobilebadge.domain.WebhookDelivery;
import com.mobilebadge.domain.WebhookEndpoint;
import com.mobilebadge.repo.WebhookDeliveryRepository;
import com.mobilebadge.repo.WebhookEndpointRepository;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 一期只落本地投递日志，不向配置的 URL 发起请求。 */
@Service
public class WebhookDispatcher {

  private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

  private final WebhookEndpointRepository endpoints;
  private final WebhookDeliveryRepository deliveries;

  public WebhookDispatcher(WebhookEndpointRepository endpoints, WebhookDeliveryRepository deliveries) {
    this.endpoints = endpoints;
    this.deliveries = deliveries;
  }

  public void dispatch(Long organizationId, String event, String detail) {
    try {
      List<WebhookEndpoint> hooks = endpoints.findByOrganizationIdAndEnabled(organizationId, true);
      for (WebhookEndpoint hook : hooks) {
        boolean subscribed = Arrays.stream(hook.events.split(",")).anyMatch(event::equals);
        if (!subscribed) {
          continue;
        }
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.webhookId = hook.id;
        delivery.eventName = event;
        delivery.statusCode = 0;
        delivery.detail = "一期未向外部 URL 投递。" + detail;
        deliveries.save(delivery);
      }
    } catch (RuntimeException ex) {
      log.warn("webhook stub failed: {}", ex.getMessage());
    }
  }
}
