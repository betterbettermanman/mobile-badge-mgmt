package com.mobilebadge.repo;

import com.mobilebadge.domain.WebhookEndpoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {
  List<WebhookEndpoint> findByOrganizationIdOrderByIdAsc(Long organizationId);

  List<WebhookEndpoint> findByOrganizationIdAndEnabled(Long organizationId, boolean enabled);
}
