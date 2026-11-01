package com.mobilebadge.service;

import com.mobilebadge.common.ApiException;
import com.mobilebadge.domain.Organization;
import com.mobilebadge.domain.Store;
import com.mobilebadge.repo.OrganizationRepository;
import com.mobilebadge.repo.StoreRepository;
import com.mobilebadge.security.AuthPrincipal;
import com.mobilebadge.security.CurrentUser;
import com.mobilebadge.web.ApiModels.OrgResponse;
import com.mobilebadge.web.ApiModels.StoreResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrgService {

  private final OrganizationRepository organizations;
  private final StoreRepository stores;

  public OrgService(OrganizationRepository organizations, StoreRepository stores) {
    this.organizations = organizations;
    this.stores = stores;
  }

  public OrgResponse current() {
    return new OrgResponse(requireOrg().id, requireOrg().name);
  }

  @Transactional
  public OrgResponse rename(String name) {
    Organization org = requireOrg();
    org.name = name.trim();
    organizations.save(org);
    return new OrgResponse(org.id, org.name);
  }

  public List<StoreResponse> listStores() {
    AuthPrincipal me = CurrentUser.require();
    return stores.findByOrganizationIdOrderByIdAsc(me.organizationId()).stream()
        .map(this::toStore)
        .toList();
  }

  @Transactional
  public StoreResponse createStore(String name) {
    AuthPrincipal me = CurrentUser.require();
    Store store = new Store();
    store.organizationId = me.organizationId();
    store.name = name.trim();
    store.enabled = true;
    stores.save(store);
    return toStore(store);
  }

  @Transactional
  public StoreResponse updateStore(Long id, String name, Boolean enabled) {
    Store store = requireStore(CurrentUser.require().organizationId(), id);
    if (name != null && !name.isBlank()) {
      if (name.trim().length() > 50) {
        throw ApiException.badRequest("STORE", "请输入门店名称");
      }
      store.name = name.trim();
    }
    if (enabled != null) {
      store.enabled = enabled;
    }
    stores.save(store);
    return toStore(store);
  }

  public Store requireStore(Long organizationId, Long storeId) {
    Store store = stores.findById(storeId).orElseThrow(() -> ApiException.badRequest("STORE", "门店不存在"));
    if (!store.organizationId.equals(organizationId)) {
      throw ApiException.forbidden();
    }
    return store;
  }

  public Long resolveStore(AuthPrincipal me, Long requested, boolean requiredEnabled) {
    if (requested == null) {
      return me.storeId();
    }
    Store store = requireStore(me.organizationId(), requested);
    if (requiredEnabled && !store.enabled) {
      throw ApiException.badRequest("STORE", "门店已停用");
    }
    return store.id;
  }

  private Organization requireOrg() {
    AuthPrincipal me = CurrentUser.require();
    return organizations.findById(me.organizationId()).orElseThrow(ApiException::notFound);
  }

  private StoreResponse toStore(Store store) {
    return new StoreResponse(store.id, store.name, store.enabled);
  }
}
