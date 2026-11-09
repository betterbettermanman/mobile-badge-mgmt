package com.mobilebadge.service;

import com.mobilebadge.adapt.LlmAdapter;
import com.mobilebadge.adapt.WebhookEvents;
import com.mobilebadge.common.ApiException;
import com.mobilebadge.domain.CaptureSession;
import com.mobilebadge.domain.ConfirmationStatus;
import com.mobilebadge.domain.NeedFollowStatus;
import com.mobilebadge.domain.NeedItem;
import com.mobilebadge.domain.NeedOrigin;
import com.mobilebadge.domain.Transcript;
import com.mobilebadge.domain.TranscriptStatus;
import com.mobilebadge.repo.CaptureSessionRepository;
import com.mobilebadge.repo.NeedItemRepository;
import com.mobilebadge.repo.TranscriptRepository;
import com.mobilebadge.repo.UserAccountRepository;
import com.mobilebadge.security.AuthPrincipal;
import com.mobilebadge.security.CurrentUser;
import com.mobilebadge.security.SessionAccessMode;
import com.mobilebadge.security.SessionAccessService;
import com.mobilebadge.web.ApiModels.NeedResponse;
import com.mobilebadge.web.ApiModels.NeedWrite;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NeedService {

  private final NeedItemRepository needs;
  private final CaptureSessionRepository sessions;
  private final TranscriptRepository transcripts;
  private final UserAccountRepository users;
  private final SessionAccessService access;
  private final LlmAdapter llmAdapter;
  private final WebhookDispatcher webhooks;
  private final AnalysisGate analysisGate;

  public NeedService(
      NeedItemRepository needs,
      CaptureSessionRepository sessions,
      TranscriptRepository transcripts,
      UserAccountRepository users,
      SessionAccessService access,
      LlmAdapter llmAdapter,
      WebhookDispatcher webhooks,
      AnalysisGate analysisGate) {
    this.needs = needs;
    this.sessions = sessions;
    this.transcripts = transcripts;
    this.users = users;
    this.access = access;
    this.llmAdapter = llmAdapter;
    this.webhooks = webhooks;
    this.analysisGate = analysisGate;
  }

  public List<NeedResponse> list(long sessionId, ConfirmationStatus status) {
    CaptureSession session = access.require(sessionId, CurrentUser.require(), SessionAccessMode.READ);
    return needs.findBySessionIdOrderByIdAsc(sessionId).stream()
        .filter(item -> status == null || item.confirmationStatus == status)
        .map(item -> toResponse(session, item))
        .toList();
  }

  @Transactional
  public NeedResponse create(long sessionId, NeedWrite write) {
    AuthPrincipal me = CurrentUser.require();
    CaptureSession session = access.require(sessionId, me, SessionAccessMode.OWNER);
    NeedItem item = new NeedItem();
    item.sessionId = sessionId;
    item.title = write.title().trim();
    item.detail = trim(write.detail());
    item.confirmationStatus = ConfirmationStatus.draft;
    item.followStatus = NeedFollowStatus.open;
    item.origin = NeedOrigin.manual;
    item.ownerUserId = me.userId();
    needs.save(item);
    return toResponse(session, item);
  }

  @Transactional
  public NeedResponse update(long sessionId, long needId, NeedWrite write) {
    AuthPrincipal me = CurrentUser.require();
    CaptureSession session = access.require(sessionId, me, SessionAccessMode.OWNER);
    NeedItem item = requireItem(sessionId, needId);
    if (item.confirmationStatus == ConfirmationStatus.confirmed) {
      throw ApiException.conflict("LOCKED", "确认后不可直接修改，请先解锁");
    }
    item.title = write.title().trim();
    item.detail = trim(write.detail());
    item.confirmationStatus = ConfirmationStatus.draft;
    item.confirmedAt = null;
    item.confirmedBy = null;
    item.updatedAt = Instant.now();
    needs.save(item);
    return toResponse(session, item);
  }

  @Transactional
  public List<NeedResponse> generate(long sessionId) {
    AuthPrincipal me = CurrentUser.require();
    CaptureSession session = access.require(sessionId, me, SessionAccessMode.OWNER);
    Transcript transcript = transcripts.findBySessionId(sessionId).orElse(null);
    if (transcript == null || transcript.fullText == null || transcript.fullText.isBlank()) {
      throw ApiException.conflict("TRANSCRIPT_PENDING", "转写完成后才能生成/确认画像");
    }
    for (NeedItem existing : needs.findBySessionIdOrderByIdAsc(sessionId)) {
      if (existing.origin == NeedOrigin.model && existing.confirmationStatus != ConfirmationStatus.confirmed) {
        needs.delete(existing);
      }
    }
    List<NeedResponse> created = new ArrayList<>();
    for (LlmAdapter.NeedDraft draft : llmAdapter.extractNeeds(transcript.fullText)) {
      NeedItem item = new NeedItem();
      item.sessionId = sessionId;
      item.title = draft.title();
      item.detail = draft.detail();
      item.confirmationStatus = ConfirmationStatus.draft;
      item.followStatus = NeedFollowStatus.open;
      item.origin = NeedOrigin.model;
      item.ownerUserId = me.userId();
      needs.save(item);
      created.add(toResponse(session, item));
    }
    return created;
  }

  @Transactional
  public NeedResponse confirm(long sessionId, long needId) {
    AuthPrincipal me = CurrentUser.require();
    CaptureSession session = access.require(sessionId, me, SessionAccessMode.OWNER);
    analysisGate.assertSucceeded(sessionId);
    if (session.transcriptStatus != TranscriptStatus.succeeded) {
      throw ApiException.conflict("TRANSCRIPT_PENDING", "转写完成后才能生成/确认画像");
    }
    NeedItem item = requireItem(sessionId, needId);
    if (item.confirmationStatus == ConfirmationStatus.rejected) {
      throw ApiException.conflict("REJECTED", "已废弃的需求不能确认");
    }
    if (item.confirmationStatus != ConfirmationStatus.confirmed) {
      item.confirmationStatus = ConfirmationStatus.confirmed;
      item.confirmedAt = Instant.now();
      item.confirmedBy = me.userId();
      item.updatedAt = item.confirmedAt;
      needs.save(item);
      webhooks.dispatch(session.organizationId, WebhookEvents.NEED_CONFIRMED, "need=" + item.id);
    }
    return toResponse(session, item);
  }

  @Transactional
  public NeedResponse unlock(long sessionId, long needId) {
    CaptureSession session = access.require(sessionId, CurrentUser.require(), SessionAccessMode.OWNER);
    NeedItem item = requireItem(sessionId, needId);
    if (item.confirmationStatus == ConfirmationStatus.confirmed) {
      item.confirmationStatus = ConfirmationStatus.draft;
      item.confirmedAt = null;
      item.confirmedBy = null;
      item.updatedAt = Instant.now();
      needs.save(item);
    }
    return toResponse(session, item);
  }

  @Transactional
  public NeedResponse discard(long sessionId, long needId) {
    CaptureSession session = access.require(sessionId, CurrentUser.require(), SessionAccessMode.OWNER);
    NeedItem item = requireItem(sessionId, needId);
    item.confirmationStatus = ConfirmationStatus.rejected;
    item.followStatus = NeedFollowStatus.discarded;
    item.confirmedAt = null;
    item.confirmedBy = null;
    item.updatedAt = Instant.now();
    needs.save(item);
    return toResponse(session, item);
  }

  @Transactional
  public NeedResponse follow(long sessionId, long needId, NeedFollowStatus followStatus) {
    CaptureSession session = access.require(sessionId, CurrentUser.require(), SessionAccessMode.OWNER);
    NeedItem item = requireItem(sessionId, needId);
    if (item.confirmationStatus != ConfirmationStatus.confirmed) {
      throw ApiException.conflict("NOT_CONFIRMED", "确认后才能更新跟进状态");
    }
    if (followStatus == NeedFollowStatus.discarded) {
      return discard(sessionId, needId);
    }
    item.followStatus = followStatus;
    item.updatedAt = Instant.now();
    needs.save(item);
    return toResponse(session, item);
  }

  public List<NeedResponse> confirmed(String query) {
    AuthPrincipal me = CurrentUser.require();
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    List<NeedResponse> result = new ArrayList<>();
    for (NeedItem item : needs.findByConfirmationStatus(ConfirmationStatus.confirmed)) {
      CaptureSession session = sessions.findById(item.sessionId).orElse(null);
      if (!access.canSee(session, me)) {
        continue;
      }
      if (!needle.isEmpty() && !contains(item.title, needle) && !contains(item.detail, needle)) {
        continue;
      }
      result.add(toResponse(session, item));
    }
    return result;
  }

  private NeedItem requireItem(long sessionId, long needId) {
    NeedItem item = needs.findById(needId).orElseThrow(ApiException::notFound);
    if (!item.sessionId.equals(sessionId)) {
      throw ApiException.notFound();
    }
    return item;
  }

  private NeedResponse toResponse(CaptureSession session, NeedItem item) {
    boolean owner = session.ownerUserId.equals(CurrentUser.require().userId());
    String ownerName = users.findById(item.ownerUserId).map(user -> user.name).orElse(null);
    return new NeedResponse(
        item.id,
        item.sessionId,
        session.customerLabel,
        item.title,
        item.detail,
        item.confirmationStatus,
        item.followStatus,
        item.origin,
        item.ownerUserId,
        ownerName,
        item.confirmedAt,
        owner && item.confirmationStatus != ConfirmationStatus.confirmed);
  }

  private static String trim(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static boolean contains(String value, String needle) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
  }
}
