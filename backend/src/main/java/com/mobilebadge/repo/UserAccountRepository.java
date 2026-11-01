package com.mobilebadge.repo;

import com.mobilebadge.domain.Role;
import com.mobilebadge.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
  Optional<UserAccount> findByAccount(String account);

  List<UserAccount> findByOrganizationIdOrderByIdAsc(Long organizationId);

  long countByOrganizationId(Long organizationId);

  long countByOrganizationIdAndRoleAndEnabled(Long organizationId, Role role, boolean enabled);
}
