package com.mobilebadge.service;

import com.mobilebadge.adapt.ObjectStorage;
import com.mobilebadge.common.ApiException;
import com.mobilebadge.domain.AudioObject;
import com.mobilebadge.domain.CaptureSession;
import com.mobilebadge.domain.CaptureStatus;
import com.mobilebadge.domain.ConfirmationStatus;
import com.mobilebadge.domain.ConsentState;
import com.mobilebadge.domain.DeviceSource;
import com.mobilebadge.domain.DeviceSourcePolicy;
import com.mobilebadge.domain.TranscriptStatus;
import com.mobilebadge.repo.AudioObjectRepository;
import com.mobilebadge.repo.CaptureSessionRepository;
import com.mobilebadge.repo.CustomerProfileRepository;
import com.mobilebadge.security.AuthPrincipal;
import com.mobilebadge.security.CurrentUser;
import com.mobilebadge.security.SessionAccessMode;
import com.mobilebadge.security.SessionAccessService;
import com.mobilebadge.web.ApiModels.PageResponse;
import com.mobilebadge.web.ApiModels.SessionResponse;
import com.mobilebadge.web.ApiModels.TranscriptResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CaptureService {

  private static final Set<String> AUDIO_EXT = Set.of("wav", "mp3", "m4a", "aac");

  private final CaptureSessionRepository sessions;
  private final AudioObjectRepository audios;
  private final CustomerProfileRepository profiles;
  private final SessionAccessService access;
  private final OrgService orgService;
  private final ConsentService consentService;
  private final DeviceService deviceService;
  private final ObjectStorage storage;
  private final AnalysisService analysis;
  private final SessionViews views;

  public CaptureService(
      CaptureSessionRepository sessions,
      AudioObjectRepository audios,
      CustomerProfileRepository profiles,
      SessionAccessService access,
      OrgService orgService,
      ConsentService consentService,
      DeviceService deviceService,
      ObjectStorage storage,
      AnalysisService analysis,
      SessionViews views) {
    this.sessions = sessions;
    this.audios = audios;
    this.profiles = profiles;
    this.access = access;
    this.orgService = orgService;
    this.consentService = consentService;
    this.deviceService = deviceService;
    this.storage = storage;
    this.analysis = analysis;
    this.views = views;
  }

  public PageResponse<SessionResponse> listMine(
      DeviceSource source, TranscriptStatus transcriptStatus, CaptureStatus captureStatus, int page, int size) {
    AuthPrincipal me = CurrentUser.require();
    Page<CaptureSession> result = sessions.searchOwn(me.userId(), source, transcriptStatus, captureStatus, pageable(page, size));
    return new PageResponse<>(result.map(views::toResponse).toList(), result.getTotalElements(), page, size);
  }

  public PageResponse<SessionResponse> listOrg(
      DeviceSource source, TranscriptStatus transcriptStatus, CaptureStatus captureStatus, int page, int size) {
    AuthPrincipal me = CurrentUser.require();
    Page<CaptureSession> result = sessions.searchOrg(me.organizationId(), source, transcriptStatus, captureStatus, pageable(page, size));
    return new PageResponse<>(result.map(views::toResponse).toList(), result.getTotalElements(), page, size);
  }

  public SessionResponse get(long id) {
    return views.toResponse(access.require(id, CurrentUser.require(), SessionAccessMode.READ));
  }

  @Transactional
  public SessionResponse create(String customerLabel, Long storeId, DeviceSource source) {
    DeviceSourcePolicy.assertCreatable(source);
    AuthPrincipal me = CurrentUser.require();
    CaptureSession session = newSession(me, customerLabel, storeId, source);
    sessions.save(session);
    return views.toResponse(session);
  }

  @Transactional
  public SessionResponse importFile(String customerLabel, Long storeId, boolean consentAcknowledged, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw ApiException.badRequest("FILE_EMPTY", "请选择音频文件");
    }
    assertAudio(file.getOriginalFilename());
    AuthPrincipal me = CurrentUser.require();
    ensureImportConsent(me, consentAcknowledged);
    CaptureSession session = newSession(me, customerLabel, storeId, DeviceSource.file);
    session.captureStatus = CaptureStatus.uploading;
    sessions.save(session);
    try {
      String key = storage.store(session.id, file.getOriginalFilename(), file.getInputStream());
      AudioObject audio = new AudioObject();
      audio.sessionId = session.id;
      audio.storageKey = key;
      audio.originalFilename = file.getOriginalFilename();
      audio.sizeBytes = file.getSize();
      audio.contentType = file.getContentType();
      audios.save(audio);
    } catch (IOException ex) {
      session.captureStatus = CaptureStatus.upload_failed;
      session.failureReason = "上传失败，请重试";
      session.updatedAt = Instant.now();
      sessions.save(session);
      throw ApiException.badRequest("UPLOAD_FAILED", "上传失败，请重试");
    }
    analysis.enqueue(session);
    return views.toResponse(sessions.findById(session.id).orElse(session));
  }

  @Transactional
  public SessionResponse start(long id) {
    AuthPrincipal me = CurrentUser.require();
    CaptureSession session = access.require(id, me, SessionAccessMode.OWNER);
    if (session.source == DeviceSource.badge) {
      throw ApiException.badRequest("BADGE_NOT_AVAILABLE", "真工牌采集将在二期开放");
    }
    if (session.source != DeviceSource.phone) {
      throw ApiException.badRequest("SOURCE", "当前会话不是手机录音");
    }
    if (session.captureStatus == CaptureStatus.cancelled) {
      throw ApiException.conflict("CANCELLED", "会话已取消");
    }
    if (session.captureStatus == CaptureStatus.recording) {
      return views.toResponse(session);
    }
    if (session.captureStatus != CaptureStatus.draft) {
      throw ApiException.conflict("STATE", "当前状态不能开始录音");
    }
    consentService.assertCanRecord(me.userId());
    if (!deviceService.hasActivePhone(me.userId())) {
      throw ApiException.conflict("DEVICE_UNBOUND", "请先绑定本机为手机录音源");
    }
    session.captureStatus = CaptureStatus.recording;
    session.updatedAt = Instant.now();
    sessions.save(session);
    deviceService.touchPhone(me.userId());
    return views.toResponse(session);
  }

  @Transactional
  public SessionResponse stop(long id) {
    AuthPrincipal me = CurrentUser.require();
    CaptureSession session = access.require(id, me, SessionAccessMode.OWNER);
    if (session.captureStatus != CaptureStatus.recording) {
      throw ApiException.conflict("STATE", "当前不在录音中");
    }
    session.captureStatus = CaptureStatus.uploading;
    session.updatedAt = Instant.now();
    sessions.save(session);
    analysis.enqueue(session);
    return views.toResponse(sessions.findById(session.id).orElse(session));
  }

  @Transactional
  public SessionResponse cancel(long id) {
    AuthPrincipal me = CurrentUser.require();
    CaptureSession session = access.require(id, me, SessionAccessMode.OWNER);
    if (session.captureStatus != CaptureStatus.recording && session.captureStatus != CaptureStatus.draft) {
      throw ApiException.conflict("STATE", "当前状态不能取消");
    }
    session.captureStatus = CaptureStatus.cancelled;
    session.updatedAt = Instant.now();
    sessions.save(session);
    return views.toResponse(session);
  }

  @Transactional
  public SessionResponse retryUpload(long id) {
    CaptureSession session = access.require(id, CurrentUser.require(), SessionAccessMode.OWNER);
    if (session.captureStatus != CaptureStatus.upload_failed) {
      throw ApiException.conflict("STATE", "当前没有失败的上传");
    }
    analysis.enqueue(session);
    return views.toResponse(sessions.findById(session.id).orElse(session));
  }

  @Transactional
  public SessionResponse rerun(long id) {
    CaptureSession session = access.require(id, CurrentUser.require(), SessionAccessMode.OWNER);
    if (session.captureStatus != CaptureStatus.uploaded && session.captureStatus != CaptureStatus.upload_failed) {
      throw ApiException.badRequest("NO_AUDIO", "尚无音频可转写");
    }
    session.updatedAt = Instant.now();
    sessions.save(session);
    analysis.enqueue(session);
    return views.toResponse(sessions.findById(session.id).orElse(session));
  }

  public TranscriptResponse transcript(long id) {
    return analysis.transcript(id);
  }

  public ConfirmationStatus profileStatus(long sessionId) {
    return profiles.findBySessionId(sessionId).map(profile -> profile.confirmationStatus).orElse(null);
  }

  private void ensureImportConsent(AuthPrincipal me, boolean consentAcknowledged) {
    if (consentService.currentState(me.userId()) == ConsentState.valid) {
      return;
    }
    if (!consentAcknowledged) {
      consentService.assertCanRecord(me.userId());
      return;
    }
    consentService.acknowledge(true, null);
  }

  private CaptureSession newSession(AuthPrincipal me, String customerLabel, Long storeId, DeviceSource source) {
    CaptureSession session = new CaptureSession();
    session.organizationId = me.organizationId();
    session.storeId = orgService.resolveStore(me, storeId, true);
    session.ownerUserId = me.userId();
    session.customerLabel = normalizeLabel(customerLabel);
    session.source = source;
    session.captureStatus = CaptureStatus.draft;
    session.createdAt = Instant.now();
    session.updatedAt = session.createdAt;
    return session;
  }

  private static String normalizeLabel(String customerLabel) {
    if (customerLabel == null || customerLabel.isBlank()) {
      return null;
    }
    String trimmed = customerLabel.trim();
    if (trimmed.length() > 80) {
      throw ApiException.badRequest("LABEL", "备注请控制在 80 字以内");
    }
    return trimmed;
  }

  private static void assertAudio(String filename) {
    String ext = "";
    if (filename != null) {
      int dot = filename.lastIndexOf('.');
      if (dot >= 0) {
        ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
      }
    }
    if (!AUDIO_EXT.contains(ext)) {
      throw ApiException.badRequest("FILE_TYPE", "仅支持 wav / mp3 / m4a / aac");
    }
  }

  private static PageRequest pageable(int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    return PageRequest.of(safePage, safeSize);
  }
}
