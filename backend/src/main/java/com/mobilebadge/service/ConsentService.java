package com.mobilebadge.service;

import com.mobilebadge.common.ApiException;
import com.mobilebadge.domain.ConsentPolicy;
import com.mobilebadge.domain.ConsentRecord;
import com.mobilebadge.domain.ConsentState;
import com.mobilebadge.repo.ConsentPolicyRepository;
import com.mobilebadge.repo.ConsentRecordRepository;
import com.mobilebadge.security.AuthPrincipal;
import com.mobilebadge.security.CurrentUser;
import com.mobilebadge.web.ApiModels.ConsentRecordResponse;
import com.mobilebadge.web.ApiModels.ConsentStatusResponse;
import com.mobilebadge.web.ApiModels.PolicyResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {

  public static final String STATEMENT = "客户已同意本次/本段服务录音";

  private final ConsentRecordRepository records;
  private final ConsentPolicyRepository policies;

  public ConsentService(ConsentRecordRepository records, ConsentPolicyRepository policies) {
    this.records = records;
    this.policies = policies;
  }

  public ConsentState currentState(Long userId) {
    return describe(userId).status();
  }

  public ConsentStatusResponse statusForCurrentUser() {
    return describe(CurrentUser.require().userId());
  }

  public List<ConsentRecordResponse> history() {
    AuthPrincipal me = CurrentUser.require();
    return records.findByUserIdOrderByGrantedAtDesc(me.userId()).stream().map(this::toRecord).toList();
  }

  @Transactional
  public ConsentStatusResponse acknowledge(Boolean acknowledged, Long sessionId) {
    if (!Boolean.TRUE.equals(acknowledged)) {
      throw ApiException.badRequest("CONSENT_REQUIRED", "请先确认客户已同意录音");
    }
    AuthPrincipal me = CurrentUser.require();
    ConsentPolicy policy = requirePolicy();
    ConsentRecord record = new ConsentRecord();
    record.organizationId = me.organizationId();
    record.userId = me.userId();
    record.sessionId = sessionId;
    record.status = ConsentState.valid;
    record.statement = STATEMENT;
    record.grantedAt = Instant.now();
    record.expiresAt = record.grantedAt.plus(policy.validityDays, ChronoUnit.DAYS);
    records.save(record);
    return describe(me.userId());
  }

  @Transactional
  public ConsentStatusResponse revoke(Long id) {
    AuthPrincipal me = CurrentUser.require();
    ConsentRecord record = records.findById(id).orElseThrow(ApiException::notFound);
    if (!record.userId.equals(me.userId())) {
      throw ApiException.forbidden();
    }
    record.status = ConsentState.revoked;
    record.revokedAt = Instant.now();
    records.save(record);
    return describe(me.userId());
  }

  public void assertCanRecord(Long userId) {
    ConsentStatusResponse status = describe(userId);
    switch (status.status()) {
      case valid -> {
        return;
      }
      case expired -> throw ApiException.forbidden("CONSENT_EXPIRED", "录音授权已过期，请重新取得客户同意");
      case revoked -> throw ApiException.forbidden("CONSENT_REVOKED", "录音授权已撤销，无法开始录音");
      default -> throw ApiException.forbidden("NO_CONSENT", "未取得录音授权，无法开始录音");
    }
  }

  public PolicyResponse policy() {
    ConsentPolicy policy = requirePolicy();
    return toPolicy(policy);
  }

  @Transactional
  public PolicyResponse updateValidity(int days) {
    ConsentPolicy policy = requirePolicy();
    policy.validityDays = days;
    policy.blockWithoutConsent = true;
    policy.requirePerSession = false;
    policies.save(policy);
    return toPolicy(policy);
  }

  private ConsentStatusResponse describe(Long userId) {
    List<ConsentRecord> all = records.findByUserIdOrderByGrantedAtDesc(userId);
    Instant now = Instant.now();
    for (ConsentRecord record : all) {
      if (record.status == ConsentState.revoked) {
        continue;
      }
      if (record.expiresAt != null && !record.expiresAt.isAfter(now)) {
        continue;
      }
      if (record.status == ConsentState.valid) {
        return new ConsentStatusResponse(ConsentState.valid, record.grantedAt, record.expiresAt, "已授权");
      }
    }
    if (all.isEmpty()) {
      return new ConsentStatusResponse(ConsentState.missing, null, null, "未授权");
    }
    ConsentRecord latest = all.get(0);
    if (latest.status == ConsentState.revoked) {
      return new ConsentStatusResponse(ConsentState.revoked, latest.grantedAt, latest.expiresAt, "录音授权已撤销，无法开始录音");
    }
    return new ConsentStatusResponse(ConsentState.expired, latest.grantedAt, latest.expiresAt, "录音授权已过期，请重新取得客户同意");
  }

  private ConsentPolicy requirePolicy() {
    return policies.findAll().stream().findFirst().orElseGet(() -> {
      ConsentPolicy created = new ConsentPolicy();
      created.blockWithoutConsent = true;
      created.validityDays = 30;
      created.requirePerSession = false;
      return policies.save(created);
    });
  }

  private PolicyResponse toPolicy(ConsentPolicy policy) {
    return new PolicyResponse(true, policy.validityDays, false, false, "无授权禁止开录");
  }

  private ConsentRecordResponse toRecord(ConsentRecord record) {
    ConsentState status = record.status;
    if (status == ConsentState.valid && record.expiresAt != null && !record.expiresAt.isAfter(Instant.now())) {
      status = ConsentState.expired;
    }
    return new ConsentRecordResponse(
        record.id,
        record.sessionId,
        status,
        record.statement,
        record.grantedAt,
        record.expiresAt,
        record.revokedAt);
  }
}
