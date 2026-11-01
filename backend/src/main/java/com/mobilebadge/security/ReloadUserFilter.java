package com.mobilebadge.security;

import com.mobilebadge.domain.UserAccount;
import com.mobilebadge.repo.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 每个请求从数据库刷新角色与停用状态，避免会话里的旧角色继续放行。 */
public class ReloadUserFilter extends OncePerRequestFilter {

  private final UserAccountRepository users;

  public ReloadUserFilter(UserAccountRepository users) {
    this.users = users;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
      UserAccount user = users.findById(principal.userId()).orElse(null);
      if (user == null || !user.enabled) {
        SecurityContextHolder.clearContext();
        response.setStatus(401);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"未登录\"}");
        return;
      }
      AuthPrincipal fresh = AuthPrincipal.from(user);
      if (!fresh.equals(principal)) {
        var replacement = new UsernamePasswordAuthenticationToken(fresh, null, fresh.authorities());
        SecurityContextHolder.getContext().setAuthentication(replacement);
      }
    }
    filterChain.doFilter(request, response);
  }
}
