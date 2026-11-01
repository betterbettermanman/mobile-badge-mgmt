package com.mobilebadge.security;

import com.mobilebadge.domain.UserAccount;
import com.mobilebadge.repo.UserAccountRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 抑制 Spring Boot 默认用户。登录本身在 AuthService 中完成，以便统一错误文案。 */
@Service
public class DbUserDetailsService implements UserDetailsService {

  private final UserAccountRepository users;

  public DbUserDetailsService(UserAccountRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    UserAccount user = users.findByAccount(username)
        .orElseThrow(() -> new UsernameNotFoundException("账号或密码不正确"));
    return new User(
        user.account,
        user.passwordHash,
        user.enabled,
        true,
        true,
        true,
        List.of(new SimpleGrantedAuthority("ROLE_" + user.role.name())));
  }
}
