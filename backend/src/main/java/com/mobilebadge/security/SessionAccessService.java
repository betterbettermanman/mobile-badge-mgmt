package com.mobilebadge.security;

import com.mobilebadge.common.ApiException;
import com.mobilebadge.domain.CaptureSession;
import com.mobilebadge.domain.Role;
import com.mobilebadge.repo.CaptureSessionRepository;
import org.springframework.stereotype.Service;

@Service
public class SessionAccessService {

  private final CaptureSessionRepository sessions;

  public SessionAccessService(CaptureSessionRepository sessions) {
    this.sessions = sessions;
  }

  public CaptureSession require(long id, AuthPrincipal me, SessionAccessMode mode) {
    CaptureSession session = sessions.findById(id).orElseThrow(ApiException::notFound);
    if (!session.organizationId.equals(me.organizationId())) {
      throw ApiException.forbidden();
    }
    boolean owner = session.ownerUserId.equals(me.userId());
    if (mode == SessionAccessMode.OWNER && !owner) {
      throw ApiException.forbidden();
    }
    if (mode == SessionAccessMode.READ && !owner && me.role() == Role.ADVISOR) {
      throw ApiException.forbidden();
    }
    return session;
  }

  public boolean canSee(CaptureSession session, AuthPrincipal me) {
    if (session == null || !session.organizationId.equals(me.organizationId())) {
      return false;
    }
    if (me.role() == Role.ADVISOR) {
      return session.ownerUserId.equals(me.userId());
    }
    return true;
  }
}
