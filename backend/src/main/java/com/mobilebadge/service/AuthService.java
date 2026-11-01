package com.mobilebadge.service;

import com.mobilebadge.common.ApiException;
import com.mobilebadge.domain.ConsentState;
import com.mobilebadge.domain.Organization;
import com.mobilebadge.domain.Store;
import com.mobilebadge.domain.UserAccount;
import com.mobilebadge.repo.OrganizationRepository;
import com.mobilebadge.repo.StoreRepository;
import com.mobilebadge.repo.UserAccountRepository;
import com.mobilebadge.security.AuthPrincipal;
import com.mobilebadge.security.CurrentUser;
import com.mobilebadge.web.ApiModels.LoginRequest;
import com.mobilebadge.web.ApiModels.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final UserAccountRepository users;
  private final OrganizationRepository organizations;
  private final StoreRepository stores;
  private final PasswordEncoder passwordEncoder;
  private final ConsentService consentService;

  public AuthService(
      UserAccountRepository users,
      OrganizationRepository organizations,
      StoreRepository stores,
      PasswordEncoder passwordEncoder,
      ConsentService consentService) {
    this.users = users;
    this.organizations = organizations;
    this.stores = stores;
    this.passwordEncoder = passwordEncoder;
    this.consentService = consentService;
  }

  public MeResponse login(LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
    String account = request.account().trim();
    UserAccount user = users.findByAccount(account).orElse(null);
    if (user == null || !user.enabled || !passwordEncoder.matches(request.password(), user.passwordHash)) {
      throw ApiException.badCredentials();
    }
    AuthPrincipal principal = AuthPrincipal.from(user);
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    HttpSession session = http.getSession(true);
    if (session != null) {
      http.changeSessionId();
    }
    new HttpSessionSecurityContextRepository().saveContext(context, http, response);
    return toMe(user);
  }

  public void logout(HttpServletRequest http) {
    SecurityContextHolder.clearContext();
    HttpSession session = http.getSession(false);
    if (session != null) {
      session.invalidate();
    }
  }

  public MeResponse me() {
    AuthPrincipal principal = CurrentUser.require();
    UserAccount user = users.findById(principal.userId()).orElseThrow(ApiException::unauthenticated);
    if (!user.enabled) {
      throw ApiException.unauthenticated();
    }
    return toMe(user);
  }

  private MeResponse toMe(UserAccount user) {
    Organization org = organizations.findById(user.organizationId).orElse(null);
    Store store = user.storeId == null ? null : stores.findById(user.storeId).orElse(null);
    ConsentState consent = consentService.currentState(user.id);
    return new MeResponse(
        user.id,
        user.account,
        user.name,
        user.role,
        user.organizationId,
        org == null ? null : org.name,
        user.storeId,
        store == null ? null : store.name,
        consent);
  }
}
