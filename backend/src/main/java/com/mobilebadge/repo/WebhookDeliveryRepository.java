package com.mobilebadge.repo;

import com.mobilebadge.domain.WebhookDelivery;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
  List<WebhookDelivery> findByWebhookIdInOrderByIdDesc(Collection<Long> webhookIds);

  List<WebhookDelivery> findByStateAndNextRetryAtLessThanEqual(String state, Instant now);
}
