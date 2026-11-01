package com.mobilebadge.service;

import com.mobilebadge.adapt.LlmAdapter;
import com.mobilebadge.adapt.WebhookEvents;
import com.mobilebadge.common.ApiException;
import com.mobilebadge.domain.CaptureSession;
import com.mobilebadge.domain.ConfirmationStatus;
import com.mobilebadge.domain.CustomerProfile;
import com.mobilebadge.domain.TranscriptStatus;
import com.mobilebadge.repo.CaptureSessionRepository;
import com.mobilebadge.repo.CustomerProfileRepository;
import com.mobilebadge.repo.UserAccountRepository;
import com.mobilebadge.security.AuthPrincipal;
import com.mobilebadge.security.CurrentUser;
import com.mobilebadge.security.SessionAccessMode;
import com.mobilebadge.security.SessionAccessService;
import com.mobilebadge.web.ApiModels.ProfileResponse;
import com.mobilebadge.web.ApiModels.ProfileWrite;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

  private static final String HINT = "确认前不会出现在「已确认」。确认后将进入已确认画像，可被检索与导出。";

  private final CustomerProfileRepository profiles;
  private final CaptureSessionRepository sessions;
  private final UserAccountRepository users;
  private final SessionAccessService access;
  private final LlmAdapter llmAdapter;
  private final WebhookDispatcher webhooks;

  public ProfileService(
      CustomerProfileRepository profiles,
      CaptureSessionRepository sessions,
      UserAccountRepository users,
      SessionAccessService access,
      LlmAdapter llmAdapter,
      WebhookDispatcher webhooks) {
    this.profiles = profiles;
    this.sessions = sessions;
    this.users = users;
    this.access = access;
    this.llmAdapter = llmAdapter;
    this.webhooks = webhooks;
  }

  public ProfileResponse get(long sessionId) {
    CaptureSession session = access.require(sessionId, CurrentUser.require(), SessionAccessMode.READ);
    return toResponse(session, profiles.findBySessionId(sessionId).orElse(null));
  }

  @Transactional
  public ProfileResponse save(long sessionId, ProfileWrite write) {
    AuthPrincipal me = CurrentUser.require();
    CaptureSession session = access.require(sessionId, me, SessionAccessMode.OWNER);
    CustomerProfile profile = profiles.findBySessionId(sessionId).orElseGet(() -> blank(sessionId));
    if (profile.confirmationStatus == ConfirmationStatus.confirmed) {
      throw ApiException.conflict("LOCKED", "确认后不可直接修改，请先解锁");
    }
    profile.appellation = trim(write.appellation(), 80);
    profile.intent = trim(write.intent(), 200);
    profile.budget = trim(write.budget(), 80);
    profile.decisionMaker = trim(write.decisionMaker(), 80);
    profile.notes = trim(write.notes(), 1000);
    profile.confirmationStatus = ConfirmationStatus.draft;
    profile.confirmedAt = null;
    profile.confirmedBy = null;
    profile.updatedAt = Instant.now();
    profiles.save(profile);
    return toResponse(session, profile);
  }

  @Transactional
  public ProfileResponse generate(long sessionId) {
    AuthPrincipal me = CurrentUser.require();
    CaptureSession session = access.require(sessionId, me, SessionAccessMode.OWNER);
    requireTranscript(session);
    CustomerProfile profile = profiles.findBySessionId(sessionId).orElseGet(() -> blank(sessionId));
    if (profile.confirmationStatus == ConfirmationStatus.confirmed) {
      throw ApiException.conflict("LOCKED", "确认后不可直接修改，请先解锁");
    }
    LlmAdapter.ProfileDraft draft = llmAdapter.extractProfile(session.customerLabel, "mock");
    profile.appellation = draft.appellation();
    profile.intent = draft.intent();
    profile.budget = draft.budget();
    profile.decisionMaker = draft.decisionMaker();
    profile.notes = draft.notes();
    profile.confirmationStatus = ConfirmationStatus.draft;
    profile.confirmedAt = null;
    profile.confirmedBy = null;
    profile.updatedAt = Instant.now();
    profiles.save(profile);
    return toResponse(session, profile);
  }

  @Transactional
  public ProfileResponse confirm(long sessionId) {
    AuthPrincipal me = CurrentUser.require();
    CaptureSession session = access.require(sessionId, me, SessionAccessMode.OWNER);
    requireTranscript(session);
    CustomerProfile profile = profiles.findBySessionId(sessionId)
        .orElseThrow(() -> ApiException.badRequest("NO_DRAFT", "请先保存草案"));
    if (profile.confirmationStatus != ConfirmationStatus.confirmed) {
      profile.confirmationStatus = ConfirmationStatus.confirmed;
      profile.confirmedAt = Instant.now();
      profile.confirmedBy = me.userId();
      profile.updatedAt = profile.confirmedAt;
      profiles.save(profile);
      webhooks.dispatch(session.organizationId, WebhookEvents.PROFILE_CONFIRMED, "profile=" + profile.id);
    }
    return toResponse(session, profile);
  }

  @Transactional
  public ProfileResponse unlock(long sessionId) {
    AuthPrincipal me = CurrentUser.require();
    CaptureSession session = access.require(sessionId, me, SessionAccessMode.OWNER);
    CustomerProfile profile = profiles.findBySessionId(sessionId)
        .orElseThrow(() -> ApiException.badRequest("NO_DRAFT", "尚无画像"));
    if (profile.confirmationStatus == ConfirmationStatus.confirmed) {
      profile.confirmationStatus = ConfirmationStatus.draft;
      profile.confirmedAt = null;
      profile.confirmedBy = null;
      profile.updatedAt = Instant.now();
      profiles.save(profile);
    }
    return toResponse(session, profile);
  }

  public List<ProfileResponse> confirmed(String query) {
    AuthPrincipal me = CurrentUser.require();
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    List<ProfileResponse> result = new ArrayList<>();
    for (CustomerProfile profile : profiles.findByConfirmationStatus(ConfirmationStatus.confirmed)) {
      CaptureSession session = sessions.findById(profile.sessionId).orElse(null);
      if (!access.canSee(session, me)) {
        continue;
      }
      if (!needle.isEmpty() && !matches(profile, needle)) {
        continue;
      }
      result.add(toResponse(session, profile));
    }
    return result;
  }

  private boolean matches(CustomerProfile profile, String needle) {
    return contains(profile.appellation, needle)
        || contains(profile.intent, needle)
        || contains(profile.budget, needle)
        || contains(profile.decisionMaker, needle)
        || contains(profile.notes, needle);
  }

  private ProfileResponse toResponse(CaptureSession session, CustomerProfile profile) {
    AuthPrincipal me = CurrentUser.require();
    boolean owner = session.ownerUserId.equals(me.userId());
    boolean editable = owner && (profile == null || profile.confirmationStatus != ConfirmationStatus.confirmed);
    String ownerName = users.findById(session.ownerUserId).map(user -> user.name).orElse(null);
    if (profile == null) {
      return new ProfileResponse(null, session.id, session.customerLabel, ownerName, null,
          null, null, null, null, null, null, editable, HINT);
    }
    return new ProfileResponse(
        profile.id,
        session.id,
        session.customerLabel,
        ownerName,
        profile.confirmationStatus,
        profile.appellation,
        profile.intent,
        profile.budget,
        profile.decisionMaker,
        profile.notes,
        profile.confirmedAt,
        editable,
        HINT);
  }

  private static CustomerProfile blank(long sessionId) {
    CustomerProfile profile = new CustomerProfile();
    profile.sessionId = sessionId;
    profile.confirmationStatus = ConfirmationStatus.draft;
    return profile;
  }

  private static void requireTranscript(CaptureSession session) {
    if (session.transcriptStatus != TranscriptStatus.succeeded) {
      throw ApiException.conflict("TRANSCRIPT_PENDING", "转写完成后才能生成/确认画像");
    }
  }

  private static String trim(String value, int max) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.length() > max) {
      throw ApiException.badRequest("FIELD", "字段超出长度限制");
    }
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static boolean contains(String value, String needle) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
  }
}
