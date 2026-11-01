package com.mobilebadge.repo;

import com.mobilebadge.domain.DeviceBindStatus;
import com.mobilebadge.domain.DeviceBinding;
import com.mobilebadge.domain.DeviceSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceBindingRepository extends JpaRepository<DeviceBinding, Long> {
  List<DeviceBinding> findByUserIdOrderByIdDesc(Long userId);

  List<DeviceBinding> findByOrganizationIdOrderByIdDesc(Long organizationId);

  Optional<DeviceBinding> findFirstByUserIdAndSourceAndStatus(Long userId, DeviceSource source, DeviceBindStatus status);

  long countByOrganizationIdAndStatus(Long organizationId, DeviceBindStatus status);
}
