package com.mobilebadge.service;

import com.mobilebadge.domain.ConfirmationStatus;
import com.mobilebadge.domain.DeviceBindStatus;
import com.mobilebadge.domain.TranscriptStatus;
import com.mobilebadge.repo.CaptureSessionRepository;
import com.mobilebadge.repo.CustomerProfileRepository;
import com.mobilebadge.repo.DeviceBindingRepository;
import com.mobilebadge.repo.NeedItemRepository;
import com.mobilebadge.repo.UserAccountRepository;
import com.mobilebadge.security.CurrentUser;
import com.mobilebadge.web.ApiModels.CapabilityRow;
import com.mobilebadge.web.ApiModels.OverviewResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OverviewService {

  private final UserAccountRepository users;
  private final CaptureSessionRepository sessions;
  private final CustomerProfileRepository profiles;
  private final NeedItemRepository needs;
  private final DeviceBindingRepository devices;

  public OverviewService(
      UserAccountRepository users,
      CaptureSessionRepository sessions,
      CustomerProfileRepository profiles,
      NeedItemRepository needs,
      DeviceBindingRepository devices) {
    this.users = users;
    this.sessions = sessions;
    this.profiles = profiles;
    this.needs = needs;
    this.devices = devices;
  }

  public OverviewResponse overview() {
    Long orgId = CurrentUser.require().organizationId();
    List<Long> sessionIds = sessions.findByOrganizationId(orgId).stream().map(session -> session.id).toList();
    Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    long pending = 0;
    long confirmed = 0;
    if (!sessionIds.isEmpty()) {
      pending = profiles.countByConfirmationStatusAndSessionIdIn(ConfirmationStatus.draft, sessionIds)
          + needs.countByConfirmationStatusAndSessionIdIn(ConfirmationStatus.draft, sessionIds);
      confirmed = profiles.countByConfirmationStatusAndSessionIdIn(ConfirmationStatus.confirmed, sessionIds);
    }
    return new OverviewResponse(
        users.countByOrganizationId(orgId),
        sessions.countByOrganizationIdAndCreatedAtGreaterThanEqual(orgId, start),
        sessions.countByOrganizationIdAndTranscriptStatus(orgId, TranscriptStatus.failed),
        pending,
        confirmed,
        devices.countByOrganizationIdAndStatus(orgId, DeviceBindStatus.bound));
  }

  public List<CapabilityRow> roles() {
    return List.of(
        new CapabilityRow("本人会话采集与确认", true, true, true),
        new CapabilityRow("查看本组织他人会话", false, true, true),
        new CapabilityRow("确认他人画像或需求", false, false, false),
        new CapabilityRow("编辑组织与门店", false, false, true),
        new CapabilityRow("邀请成员与改角色", false, false, true),
        new CapabilityRow("配置授权策略", false, false, true),
        new CapabilityRow("查看组织设备", false, true, true),
        new CapabilityRow("绑定本人手机录音源", true, true, true),
        new CapabilityRow("导出", false, false, true),
        new CapabilityRow("Webhook", false, false, true));
  }
}
