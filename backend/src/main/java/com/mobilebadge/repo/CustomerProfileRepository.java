package com.mobilebadge.repo;

import com.mobilebadge.domain.ConfirmationStatus;
import com.mobilebadge.domain.CustomerProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {
  Optional<CustomerProfile> findBySessionId(Long sessionId);

  List<CustomerProfile> findBySessionIdIn(Collection<Long> sessionIds);

  List<CustomerProfile> findByConfirmationStatus(ConfirmationStatus confirmationStatus);

  long countByConfirmationStatusAndSessionIdIn(ConfirmationStatus confirmationStatus, Collection<Long> sessionIds);
}
