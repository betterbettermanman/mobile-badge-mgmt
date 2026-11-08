package com.mobilebadge.repo;

import com.mobilebadge.domain.WebhookPolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookPolicyRepository extends JpaRepository<WebhookPolicy, Long> {

  Optional<WebhookPolicy> findByOrganizationId(Long organizationId);
}
