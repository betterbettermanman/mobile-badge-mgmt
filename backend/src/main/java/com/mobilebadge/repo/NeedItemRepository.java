package com.mobilebadge.repo;

import com.mobilebadge.domain.ConfirmationStatus;
import com.mobilebadge.domain.NeedItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NeedItemRepository extends JpaRepository<NeedItem, Long> {
  List<NeedItem> findBySessionIdOrderByIdAsc(Long sessionId);

  List<NeedItem> findByConfirmationStatus(ConfirmationStatus confirmationStatus);

  long countByConfirmationStatusAndSessionIdIn(ConfirmationStatus confirmationStatus, Collection<Long> sessionIds);
}
