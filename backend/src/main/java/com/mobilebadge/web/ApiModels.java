package com.mobilebadge.web;

import com.mobilebadge.domain.CaptureStatus;
import com.mobilebadge.domain.ConfirmationStatus;
import com.mobilebadge.domain.ConsentState;
import com.mobilebadge.domain.DeviceBindStatus;
import com.mobilebadge.domain.DeviceSource;
import com.mobilebadge.domain.ExportFormat;
import com.mobilebadge.domain.JobStatus;
import com.mobilebadge.domain.NeedFollowStatus;
import com.mobilebadge.domain.NeedOrigin;
import com.mobilebadge.domain.Role;
import com.mobilebadge.domain.TranscriptStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApiModels {

  private ApiModels() {
  }

  public record LoginRequest(
      @NotBlank(message = "请输入账号") String account,
      @NotBlank(message = "请输入密码") String password) {
  }

  public record MeResponse(
      Long id,
      String account,
      String name,
      Role role,
      Long organizationId,
      String organizationName,
      Long storeId,
      String storeName,
      ConsentState consentStatus) {
  }

  public record OrgResponse(Long id, String name) {
  }

  public record OrgUpdate(@NotBlank(message = "请输入组织名称") @Size(max = 80, message = "组织名称过长") String name) {
  }

  public record StoreResponse(Long id, String name, boolean enabled) {
  }

  public record StoreWrite(
      @NotBlank(message = "请输入门店名称") @Size(max = 50, message = "请输入门店名称") String name,
      Boolean enabled) {
  }

  public record MemberResponse(
      Long id,
      String account,
      String name,
      Role role,
      Long storeId,
      String storeName,
      boolean enabled,
      Instant createdAt) {
  }

  public record MemberCreate(
      @NotBlank(message = "请输入账号") @Size(min = 3, max = 32, message = "请输入账号") String account,
      @NotBlank(message = "请输入姓名") @Size(max = 64, message = "请输入姓名") String name,
      @NotBlank(message = "请输入密码") @Size(min = 6, max = 64, message = "密码至少 6 位") String password,
      @NotNull(message = "请选择角色") Role role,
      Long storeId) {
  }

  public record MemberUpdate(String name, Role role, Long storeId, Boolean enabled, String password) {
  }

  public record SessionCreate(String customerLabel, Long storeId, @NotNull(message = "请选择采集来源") DeviceSource source) {
  }

  public record SessionResponse(
      Long id,
      String customerLabel,
      Long storeId,
      String storeName,
      Long ownerUserId,
      String ownerName,
      DeviceSource source,
      CaptureStatus captureStatus,
      TranscriptStatus transcriptStatus,
      ConfirmationStatus profileStatus,
      ConsentState consentStatus,
      String failureReason,
      Instant createdAt,
      Instant updatedAt) {
  }

  public record PageResponse<T>(List<T> items, long total, int page, int size) {
  }

  public record DeviceBindRequest(@NotNull(message = "请选择采集来源") DeviceSource source, String displayName) {
  }

  public record DeviceResponse(
      Long id,
      Long userId,
      String userName,
      DeviceSource source,
      String displayName,
      DeviceBindStatus status,
      Instant lastActiveAt,
      boolean phase1Available) {
  }

  public record SourceMeta(DeviceSource code, String label, boolean available, String note) {
  }

  public record ConsentCreate(@NotNull(message = "请先确认客户已同意录音") Boolean acknowledged, Long sessionId) {
  }

  public record ConsentStatusResponse(ConsentState status, Instant grantedAt, Instant expiresAt, String message) {
  }

  public record ConsentRecordResponse(
      Long id,
      Long sessionId,
      ConsentState status,
      String statement,
      Instant grantedAt,
      Instant expiresAt,
      Instant revokedAt) {
  }

  public record PolicyResponse(
      boolean blockWithoutConsent,
      int validityDays,
      boolean requirePerSession,
      boolean remindOnlyAvailable,
      String modeLabel) {
  }

  public record PolicyUpdate(@NotNull @Min(1) @Max(365) Integer validityDays) {
  }

  public record SentenceResponse(String speaker, String text) {
  }

  public record TranscriptResponse(
      Long sessionId,
      TranscriptStatus status,
      String fullText,
      List<SentenceResponse> sentences,
      String note,
      String engine) {
  }

  public record ProfileWrite(String appellation, String intent, String budget, String decisionMaker, String notes) {
  }

  public record ProfileResponse(
      Long id,
      Long sessionId,
      String customerLabel,
      String ownerName,
      ConfirmationStatus confirmationStatus,
      String appellation,
      String intent,
      String budget,
      String decisionMaker,
      String notes,
      Instant confirmedAt,
      boolean editable,
      String hint) {
  }

  public record NeedWrite(
      @NotBlank(message = "请输入需求标题") @Size(max = 120, message = "需求标题过长") String title,
      @Size(max = 500) String detail) {
  }

  public record FollowWrite(@NotNull NeedFollowStatus followStatus) {
  }

  public record NeedResponse(
      Long id,
      Long sessionId,
      String customerLabel,
      String title,
      String detail,
      ConfirmationStatus confirmationStatus,
      NeedFollowStatus followStatus,
      NeedOrigin origin,
      Long ownerUserId,
      String ownerName,
      Instant confirmedAt,
      boolean editable) {
  }

  public record ExportCreate(
      LocalDate from,
      LocalDate to,
      Boolean includeConfirmedProfiles,
      Boolean includeConfirmedNeeds,
      Boolean includeSessionMeta,
      Boolean includeTranscript,
      @NotNull(message = "请选择导出格式") ExportFormat format) {
  }

  public record ExportJobResponse(
      Long id,
      ExportFormat format,
      JobStatus status,
      LocalDate from,
      LocalDate to,
      boolean includeConfirmedProfiles,
      boolean includeConfirmedNeeds,
      boolean includeSessionMeta,
      boolean includeTranscript,
      String message,
      String downloadPath,
      Instant createdAt) {
  }

  public record WebhookCreate(
      @NotBlank(message = "请输入合法的 HTTPS URL") String url,
      @NotBlank(message = "请输入密钥") @Size(min = 8, max = 80, message = "密钥至少 8 位") String secret,
      @NotEmpty(message = "请选择订阅事件") List<String> events,
      Boolean enabled) {
  }

  public record WebhookUpdate(String url, String secret, List<String> events, Boolean enabled) {
  }

  public record WebhookResponse(Long id, String url, String secretMasked, List<String> events, boolean enabled, Instant createdAt) {
  }

  public record WebhookDeliveryResponse(Long id, Long webhookId, String eventName, int statusCode, String detail, Instant createdAt) {
  }

  public record OverviewResponse(
      long memberCount,
      long todaySessionCount,
      long transcriptFailedCount,
      long pendingDraftCount,
      long confirmedProfileCount,
      long boundDeviceCount) {
  }

  public record CapabilityRow(String capability, boolean advisor, boolean supervisor, boolean admin) {
  }
}
