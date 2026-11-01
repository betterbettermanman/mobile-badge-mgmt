package com.mobilebadge.repo;

import com.mobilebadge.domain.Store;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {
  List<Store> findByOrganizationIdOrderByIdAsc(Long organizationId);
}
