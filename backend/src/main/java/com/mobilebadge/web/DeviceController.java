package com.mobilebadge.web;

import com.mobilebadge.domain.DeviceSource;
import com.mobilebadge.domain.DeviceSourcePolicy;
import com.mobilebadge.service.DeviceService;
import com.mobilebadge.web.ApiModels.DeviceBindRequest;
import com.mobilebadge.web.ApiModels.DeviceResponse;
import com.mobilebadge.web.ApiModels.SourceMeta;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeviceController {

  private final DeviceService deviceService;

  public DeviceController(DeviceService deviceService) {
    this.deviceService = deviceService;
  }

  @GetMapping("/api/meta/device-sources")
  public List<SourceMeta> sources() {
    return List.of(
        new SourceMeta(DeviceSource.phone, "手机", true, "绑定手机录音源后可开录"),
        new SourceMeta(DeviceSource.file, "文件", true, "导入时自动标记，无需绑定"),
        new SourceMeta(DeviceSource.badge, "工牌", false, DeviceSourcePolicy.BADGE_NOTE));
  }

  @GetMapping("/api/devices")
  public List<DeviceResponse> mine() {
    return deviceService.mine();
  }

  @PostMapping("/api/devices/bind")
  public DeviceResponse bind(@Valid @RequestBody DeviceBindRequest request) {
    return deviceService.bind(request.source(), request.displayName());
  }

  @PostMapping("/api/devices/{id}/unbind")
  public DeviceResponse unbind(@PathVariable Long id) {
    return deviceService.unbind(id);
  }

  @GetMapping("/api/admin/devices")
  @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
  public List<DeviceResponse> organization(@RequestParam(required = false) DeviceSource source) {
    return deviceService.organization(source);
  }

  @PostMapping("/api/admin/devices/{id}/disable")
  @PreAuthorize("hasRole('ADMIN')")
  public DeviceResponse disable(@PathVariable Long id) {
    return deviceService.disable(id);
  }
}
