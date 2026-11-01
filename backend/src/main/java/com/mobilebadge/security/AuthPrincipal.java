package com.mobilebadge.security;

import com.mobilebadge.domain.Role;
import com.mobilebadge.domain.UserAccount;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthPrincipal(
    Long userId,
    String account,
    String name,
    Role role,
    Long organizationId,
    Long storeId) {

  public static AuthPrincipal from(UserAccount user) {
    return new AuthPrincipal(user.id, user.account, user.name, user.role, user.organizationId, user.storeId);
  }

  public Collection<? extends GrantedAuthority> authorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  public boolean admin() {
    return role == Role.ADMIN;
  }

  public boolean advisor() {
    return role == Role.ADVISOR;
  }
}
