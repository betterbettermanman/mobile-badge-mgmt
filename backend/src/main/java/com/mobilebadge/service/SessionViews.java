package com.mobilebadge.service;

import com.mobilebadge.domain.CaptureSession;
import com.mobilebadge.domain.Store;
import com.mobilebadge.domain.UserAccount;
import com.mobilebadge.repo.AnalysisJobRepository;
import com.mobilebadge.repo.CustomerProfileRepository;
import com.mobilebadge.repo.StoreRepository;
import com.mobilebadge.repo.UserAccountRepository;
import com.mobilebadge.web.ApiModels.SessionResponse;
import org.springframework.stereotype.Component;

@Component
public class SessionViews {

  private final StoreRepository stores;
  private final UserAccountRepository users;
  private final CustomerProfileRepository profiles;
  private final ConsentService consentService;
  private final AnalysisJobRepository jobs;

  public SessionViews(
      StoreRepository stores,
      UserAccountRepository users,
      CustomerProfileRepository profiles,
      ConsentService consentService,
      AnalysisJobRepository jobs) {
    this.stores = stores;
    this.users = users;
    this.profiles = profiles;
    this.consentService = consentService;
    this.jobs = jobs;
  }

  public SessionResponse toResponse(CaptureSession session) {
    Store store = session.storeId == null ? null : stores.findById(session.storeId).orElse(null);
    UserAccount owner = users.findById(session.ownerUserId).orElse(null);
    var profile = profiles.findBySessionId(session.id).orElse(null);
    return new SessionResponse(
        session.id,
        session.customerLabel,
        session.storeId,
        store == null ? null : store.name,
        session.ownerUserId,
        owner == null ? null : owner.name,
        session.source,
        session.captureStatus,
        session.transcriptStatus,
        profile == null ? null : profile.confirmationStatus,
        consentService.currentState(session.ownerUserId),
        session.failureReason,
        session.createdAt,
        session.updatedAt,
        AnalysisCopy.uiStatus(jobs.findBySessionId(session.id).orElse(null)));
  }
}
