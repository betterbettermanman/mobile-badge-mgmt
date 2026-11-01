package com.mobilebadge.service;

import com.mobilebadge.common.ApiException;
import com.mobilebadge.domain.DeviceBindStatus;
import com.mobilebadge.domain.DeviceBinding;
import com.mobilebadge.domain.DeviceSource;
import com.mobilebadge.domain.DeviceSourcePolicy;
import com.mobilebadge.domain.UserAccount;
import com.mobilebadge.repo.DeviceBindingRepository;
import com.mobilebadge.repo.UserAccountRepository;
import com.mobilebadge.security.AuthPrincipal;
import com.mobilebadge.security.CurrentUser;
import com.mobilebadge.web.ApiModels.DeviceResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

  private final DeviceBindingRepository devices;
  private final UserAccountRepository users;

  public DeviceService(DeviceBindingRepository devices, UserAccountRepository users) {
    this.devices = devices;
    this.users = users;
  }

  public List<DeviceResponse> mine() {
    AuthPrincipal me = CurrentUser.require();
    return devices.findByUserIdOrderByIdDesc(me.userId()).stream().map(this::toResponse).toList();
  }

  public List<DeviceResponse> organization(DeviceSource source) {
    AuthPrincipal me = CurrentUser.require();
    return devices.findByOrganizationIdOrderByIdDesc(me.organizationId()).stream()
        .filter(device -> source == null || device.source == source)
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public DeviceResponse bind(DeviceSource source, String displayName) {
    DeviceSourcePolicy.assertBindable(source);
    AuthPrincipal me = CurrentUser.require();
    DeviceBinding existing = devices
        .findFirstByUserIdAndSourceAndStatus(me.userId(), DeviceSource.phone, DeviceBindStatus.bound)
        .orElse(null);
    if (existing != null) {
      existing.displayName = nameOrDefault(displayName);
      existing.lastActiveAt = Instant.now();
      devices.save(existing);
      return toResponse(existing);
    }
    DeviceBinding created = new DeviceBinding();
    created.organizationId = me.organizationId();
    created.userId = me.userId();
    created.source = DeviceSource.phone;
    created.displayName = nameOrDefault(displayName);
    created.status = DeviceBindStatus.bound;
    created.lastActiveAt = Instant.now();
    devices.save(created);
    return toResponse(created);
  }

  @Transactional
  public DeviceResponse unbind(Long id) {
    AuthPrincipal me = CurrentUser.require();
    DeviceBinding device = devices.findById(id).orElseThrow(ApiException::notFound);
    if (!device.userId.equals(me.userId())) {
      throw ApiException.forbidden();
    }
    device.status = DeviceBindStatus.unbound;
    device.lastActiveAt = Instant.now();
    devices.save(device);
    return toResponse(device);
  }

  @Transactional
  public DeviceResponse disable(Long id) {
    AuthPrincipal me = CurrentUser.require();
    DeviceBinding device = devices.findById(id).orElseThrow(ApiException::notFound);
    if (!device.organizationId.equals(me.organizationId())) {
      throw ApiException.forbidden();
    }
    device.status = DeviceBindStatus.disabled;
    devices.save(device);
    return toResponse(device);
  }

  public boolean hasActivePhone(Long userId) {
    return devices.findFirstByUserIdAndSourceAndStatus(userId, DeviceSource.phone, DeviceBindStatus.bound).isPresent();
  }

  @Transactional
  public void touchPhone(Long userId) {
    devices.findFirstByUserIdAndSourceAndStatus(userId, DeviceSource.phone, DeviceBindStatus.bound)
        .ifPresent(device -> {
          device.lastActiveAt = Instant.now();
          devices.save(device);
        });
  }

  private DeviceResponse toResponse(DeviceBinding device) {
    UserAccount user = users.findById(device.userId).orElse(null);
    return new DeviceResponse(
        device.id,
        device.userId,
        user == null ? null : user.name,
        device.source,
        device.displayName,
        device.status,
        device.lastActiveAt,
        device.source != DeviceSource.badge);
  }

  private static String nameOrDefault(String displayName) {
    if (displayName == null || displayName.isBlank()) {
      return "本机";
    }
    String trimmed = displayName.trim();
    return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
  }
}
