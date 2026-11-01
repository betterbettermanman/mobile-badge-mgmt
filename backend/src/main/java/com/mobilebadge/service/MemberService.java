package com.mobilebadge.service;

import com.mobilebadge.common.ApiException;
import com.mobilebadge.domain.Role;
import com.mobilebadge.domain.Store;
import com.mobilebadge.domain.UserAccount;
import com.mobilebadge.repo.StoreRepository;
import com.mobilebadge.repo.UserAccountRepository;
import com.mobilebadge.security.AuthPrincipal;
import com.mobilebadge.security.CurrentUser;
import com.mobilebadge.web.ApiModels.MemberCreate;
import com.mobilebadge.web.ApiModels.MemberResponse;
import com.mobilebadge.web.ApiModels.MemberUpdate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

  private final UserAccountRepository users;
  private final StoreRepository stores;
  private final PasswordEncoder passwordEncoder;

  public MemberService(UserAccountRepository users, StoreRepository stores, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.stores = stores;
    this.passwordEncoder = passwordEncoder;
  }

  public List<MemberResponse> list() {
    AuthPrincipal me = CurrentUser.require();
    Map<Long, Store> storeMap = stores.findByOrganizationIdOrderByIdAsc(me.organizationId()).stream()
        .collect(Collectors.toMap(store -> store.id, Function.identity()));
    return users.findByOrganizationIdOrderByIdAsc(me.organizationId()).stream()
        .map(user -> toResponse(user, storeMap.get(user.storeId)))
        .toList();
  }

  @Transactional
  public MemberResponse create(MemberCreate request) {
    AuthPrincipal me = CurrentUser.require();
    String account = request.account().trim();
    if (users.findByAccount(account).isPresent()) {
      throw ApiException.conflict("ACCOUNT_EXISTS", "账号已存在");
    }
    Long storeId = null;
    if (request.storeId() != null) {
      Store store = stores.findById(request.storeId()).orElseThrow(() -> ApiException.badRequest("STORE", "门店不存在"));
      if (!store.organizationId.equals(me.organizationId())) {
        throw ApiException.forbidden();
      }
      storeId = store.id;
    }
    UserAccount user = new UserAccount();
    user.organizationId = me.organizationId();
    user.storeId = storeId;
    user.account = account;
    user.name = request.name().trim();
    user.passwordHash = passwordEncoder.encode(request.password());
    user.role = request.role();
    user.enabled = true;
    users.save(user);
    Store store = storeId == null ? null : stores.findById(storeId).orElse(null);
    return toResponse(user, store);
  }

  @Transactional
  public MemberResponse update(Long id, MemberUpdate request) {
    AuthPrincipal me = CurrentUser.require();
    UserAccount user = users.findById(id).orElseThrow(ApiException::notFound);
    if (!user.organizationId.equals(me.organizationId())) {
      throw ApiException.forbidden();
    }
    boolean disabling = Boolean.FALSE.equals(request.enabled());
    boolean demotingAdmin = user.role == Role.ADMIN && request.role() != null && request.role() != Role.ADMIN;
    if ((disabling || demotingAdmin) && user.role == Role.ADMIN) {
      if (user.id.equals(me.userId()) && disabling) {
        throw ApiException.conflict("SELF", "不能停用当前登录账号");
      }
      long admins = users.countByOrganizationIdAndRoleAndEnabled(me.organizationId(), Role.ADMIN, true);
      if (admins <= 1 && user.enabled) {
        throw ApiException.conflict("LAST_ADMIN", "至少保留一名启用的管理员");
      }
    }
    if (request.name() != null && !request.name().isBlank()) {
      user.name = request.name().trim();
    }
    if (request.role() != null) {
      user.role = request.role();
    }
    if (request.storeId() != null) {
      Store store = stores.findById(request.storeId()).orElseThrow(() -> ApiException.badRequest("STORE", "门店不存在"));
      if (!store.organizationId.equals(me.organizationId())) {
        throw ApiException.forbidden();
      }
      user.storeId = store.id;
    }
    if (request.enabled() != null) {
      user.enabled = request.enabled();
    }
    if (request.password() != null && !request.password().isBlank()) {
      if (request.password().length() < 6) {
        throw ApiException.badRequest("PASSWORD", "密码至少 6 位");
      }
      user.passwordHash = passwordEncoder.encode(request.password());
    }
    users.save(user);
    Store store = user.storeId == null ? null : stores.findById(user.storeId).orElse(null);
    return toResponse(user, store);
  }

  private MemberResponse toResponse(UserAccount user, Store store) {
    return new MemberResponse(
        user.id,
        user.account,
        user.name,
        user.role,
        user.storeId,
        store == null ? null : store.name,
        user.enabled,
        user.createdAt);
  }
}
