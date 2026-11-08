package com.mobilebadge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilebadge.adapt.AsrAdapter;
import com.mobilebadge.adapt.AudioTokenService;
import com.mobilebadge.adapt.LlmAdapter;
import com.mobilebadge.adapt.SecretRedactor;
import com.mobilebadge.adapt.VendorCallException;
import com.mobilebadge.common.ApiException;
import com.mobilebadge.config.AppProperties;
import com.mobilebadge.domain.AnalysisJob;
import com.mobilebadge.domain.AnalysisStage;
import com.mobilebadge.domain.CaptureSession;
import com.mobilebadge.domain.CaptureStatus;
import com.mobilebadge.domain.ConfirmationStatus;
import com.mobilebadge.domain.CustomerProfile;
import com.mobilebadge.domain.JobStatus;
import com.mobilebadge.domain.NeedFollowStatus;
import com.mobilebadge.domain.NeedItem;
import com.mobilebadge.domain.NeedOrigin;
import com.mobilebadge.domain.Transcript;
import com.mobilebadge.domain.TranscriptStatus;
import com.mobilebadge.repo.AnalysisJobRepository;
import com.mobilebadge.repo.AudioObjectRepository;
import com.mobilebadge.repo.CaptureSessionRepository;
import com.mobilebadge.repo.CustomerProfileRepository;
import com.mobilebadge.repo.NeedItemRepository;
import com.mobilebadge.repo.TranscriptRepository;
import com.mobilebadge.security.CurrentUser;
import com.mobilebadge.security.SessionAccessMode;
import com.mobilebadge.security.SessionAccessService;
import com.mobilebadge.web.ApiModels.AnalysisResponse;
import com.mobilebadge.web.ApiModels.SentenceResponse;
import com.mobilebadge.web.ApiModels.TranscriptResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisService {

  private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

  private final AnalysisJobRepository jobs;
  private final CaptureSessionRepository sessions;
  private final TranscriptRepository transcripts;
  private final AudioObjectRepository audios;
  private final CustomerProfileRepository profiles;
  private final NeedItemRepository needs;
  private final SessionAccessService access;
  private final AsrAdapter asr;
  private final LlmAdapter llm;
  private final AudioTokenService audioTokens;
  private final AppProperties properties;
  private final AnalysisControl control;
  private final ObjectMapper objectMapper;

  public AnalysisService(
      AnalysisJobRepository jobs,
      CaptureSessionRepository sessions,
      TranscriptRepository transcripts,
      AudioObjectRepository audios,
      CustomerProfileRepository profiles,
      NeedItemRepository needs,
      SessionAccessService access,
      AsrAdapter asr,
      LlmAdapter llm,
      AudioTokenService audioTokens,
      AppProperties properties,
      AnalysisControl control,
      ObjectMapper objectMapper) {
    this.jobs = jobs;
    this.sessions = sessions;
    this.transcripts = transcripts;
    this.audios = audios;
    this.profiles = profiles;
    this.needs = needs;
    this.access = access;
    this.asr = asr;
    this.llm = llm;
    this.audioTokens = audioTokens;
    this.properties = properties;
    this.control = control;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void enqueue(CaptureSession session) {
    AnalysisJob job = jobs.findBySessionId(session.id).orElseGet(() -> {
      AnalysisJob created = new AnalysisJob();
      created.sessionId = session.id;
      created.createdAt = Instant.now();
      return created;
    });
    job.status = JobStatus.queued;
    job.stage = AnalysisStage.none;
    job.vendor = properties.getAsr().getProvider();
    job.vendorTaskId = null;
    job.attempt = 0;
    job.retryCount = 0;
    job.lastError = null;
    job.updatedAt = Instant.now();
    jobs.save(job);
    session.captureStatus = CaptureStatus.uploaded;
    session.transcriptStatus = TranscriptStatus.queued;
    session.failureReason = null;
    session.updatedAt = Instant.now();
    sessions.save(session);
    if (properties.inlineAnalysis()) {
      drain(job.id);
    }
  }

  @Transactional
  public void tick() {
    for (AnalysisJob job : jobs.findByStatusIn(List.of(JobStatus.queued, JobStatus.processing))) {
      try {
        applyStep(job.id);
      } catch (RuntimeException ex) {
        log.warn("analysis step failed: {}", SecretRedactor.redact(ex.getMessage(), properties.secretValues()));
      }
    }
  }

  @Transactional
  public void kick(long sessionId) {
    AnalysisJob job = jobs.findBySessionId(sessionId).orElse(null);
    if (job != null) {
      drain(job.id);
    }
  }

  @Transactional
  public void onCallback(String secret, String taskId) {
    String expected = properties.getAsr().getVolc().getCallbackSecret();
    if (expected == null || expected.isBlank() || !SecretRedactor.constantEquals(expected, secret == null ? "" : secret)) {
      throw new ApiException(401, "CALLBACK_UNAUTHORIZED", "回调鉴权失败");
    }
    if (taskId == null || taskId.isBlank()) {
      return;
    }
    jobs.findByVendorTaskId(taskId).ifPresent(job -> drain(job.id));
  }

  @Transactional
  public AnalysisResponse retry(long sessionId) {
    CaptureSession session = access.require(sessionId, CurrentUser.require(), SessionAccessMode.OWNER);
    AnalysisJob job = jobs.findBySessionId(sessionId).orElseThrow(() -> ApiException.conflict("NO_JOB", "尚无分析任务"));
    if (job.status != JobStatus.failed) {
      throw ApiException.conflict("STATE", "当前分析没有失败，不能重试");
    }
    if (!AnalysisCopy.canRetry(job)) {
      throw ApiException.conflict("RETRY_LIMIT", "已达重试上限（3 次），请联系管理员排查配额/配置或稍后由运维介入。");
    }
    job.retryCount += 1;
    job.status = JobStatus.queued;
    job.stage = AnalysisStage.none;
    job.vendorTaskId = null;
    job.lastError = null;
    job.updatedAt = Instant.now();
    jobs.save(job);
    session.transcriptStatus = TranscriptStatus.queued;
    session.failureReason = null;
    session.updatedAt = Instant.now();
    sessions.save(session);
    if (properties.inlineAnalysis()) {
      drain(job.id);
    }
    return toResponse(jobs.findBySessionId(sessionId).orElse(job));
  }

  public AnalysisResponse view(long sessionId) {
    access.require(sessionId, CurrentUser.require(), SessionAccessMode.READ);
    return toResponse(jobs.findBySessionId(sessionId).orElse(null), sessionId);
  }

  public TranscriptResponse transcript(long sessionId) {
    CaptureSession session = access.require(sessionId, CurrentUser.require(), SessionAccessMode.READ);
    AnalysisJob job = jobs.findBySessionId(sessionId).orElse(null);
    Transcript transcript = transcripts.findBySessionId(sessionId).orElse(null);
    boolean visible = transcript != null && (session.transcriptStatus == TranscriptStatus.succeeded
        || (job != null && (job.stage == AnalysisStage.llm || job.status == JobStatus.succeeded)));
    if (!visible) {
      return new TranscriptResponse(session.id, session.transcriptStatus, null, List.of(), noteFor(session, job), engine());
    }
    return new TranscriptResponse(
        session.id,
        session.transcriptStatus,
        transcript.fullText,
        readSentences(transcript.sentencesJson),
        noteFor(session, job),
        engine());
  }

  private void drain(long jobId) {
    for (int i = 0; i < 8; i++) {
      AnalysisJob job = jobs.findById(jobId).orElseThrow();
      if (job.status == JobStatus.succeeded || job.status == JobStatus.failed) {
        return;
      }
      if (control.pauseBeforeLlm() && job.status == JobStatus.processing && job.stage == AnalysisStage.llm) {
        return;
      }
      if (!applyStep(jobId)) {
        return;
      }
    }
  }

  private boolean applyStep(long jobId) {
    AnalysisJob job = jobs.findById(jobId).orElseThrow();
    CaptureSession session = sessions.findById(job.sessionId).orElseThrow();
    if (job.status == JobStatus.succeeded || job.status == JobStatus.failed) {
      return false;
    }
    try {
      if (job.status == JobStatus.queued) {
        return beginAsr(job, session);
      }
      if (job.stage == AnalysisStage.asr && asr.async()) {
        return pollAsr(job, session);
      }
      if (job.stage == AnalysisStage.llm) {
        return runLlm(job, session);
      }
      return false;
    } catch (VendorCallException ex) {
      fail(job, session, ex.getMessage());
      return true;
    } catch (RuntimeException ex) {
      fail(job, session, "云分析暂时失败");
      log.warn("analysis error: {}", SecretRedactor.redact(ex.getMessage(), properties.secretValues()));
      return true;
    }
  }

  private boolean beginAsr(AnalysisJob job, CaptureSession session) {
    job.status = JobStatus.processing;
    job.stage = AnalysisStage.asr;
    job.attempt += 1;
    job.updatedAt = Instant.now();
    session.transcriptStatus = TranscriptStatus.processing;
    session.failureReason = null;
    session.updatedAt = Instant.now();
    String key = audios.findFirstBySessionIdOrderByIdDesc(session.id).map(audio -> audio.storageKey).orElse(null);
    if (asr.async()) {
      if (key == null) {
        fail(job, session, "尚无音频可转写");
        return true;
      }
      String url = audioTokens.presign(key, Duration.ofSeconds(Math.max(properties.getAudioTokenTtlSeconds(), 60)));
      job.vendorTaskId = asr.submit(session.id, key, url, properties.hotwords());
      jobs.save(job);
      sessions.save(session);
      return true;
    }
    AsrAdapter.AsrResult result = asr.transcribe(session.id, key);
    saveTranscript(job, session, result);
    job.stage = AnalysisStage.llm;
    job.updatedAt = Instant.now();
    jobs.save(job);
    sessions.save(session);
    return true;
  }

  private boolean pollAsr(AnalysisJob job, CaptureSession session) {
    if (job.vendorTaskId == null || job.vendorTaskId.isBlank()) {
      return beginAsr(job, session);
    }
    AsrAdapter.AsrPoll poll = asr.fetch(job.vendorTaskId);
    if (poll.state() == AsrAdapter.AsrPollState.PENDING) {
      return false;
    }
    if (poll.state() == AsrAdapter.AsrPollState.FAILED || poll.result() == null) {
      fail(job, session, poll.error() == null ? "云分析暂时失败" : poll.error());
      return true;
    }
    saveTranscript(job, session, poll.result());
    job.stage = AnalysisStage.llm;
    job.updatedAt = Instant.now();
    jobs.save(job);
    sessions.save(session);
    return true;
  }

  private boolean runLlm(AnalysisJob job, CaptureSession session) {
    Transcript transcript = transcripts.findBySessionId(session.id).orElse(null);
    String text = transcript == null ? "" : transcript.fullText;
    LlmAdapter.Extraction extraction = llm.extract(session.customerLabel, text);
    writeDrafts(session, extraction);
    job.status = JobStatus.succeeded;
    job.lastError = null;
    job.updatedAt = Instant.now();
    session.transcriptStatus = TranscriptStatus.succeeded;
    session.failureReason = null;
    session.updatedAt = Instant.now();
    jobs.save(job);
    sessions.save(session);
    return true;
  }

  private void saveTranscript(AnalysisJob job, CaptureSession session, AsrAdapter.AsrResult result) {
    try {
      Transcript transcript = transcripts.findBySessionId(session.id).orElseGet(Transcript::new);
      transcript.sessionId = session.id;
      transcript.fullText = result.fullText();
      transcript.sentencesJson = objectMapper.writeValueAsString(result.sentences());
      transcript.updatedAt = Instant.now();
      transcripts.save(transcript);
      job.transcriptJson = transcript.sentencesJson;
    } catch (JsonProcessingException ex) {
      throw new VendorCallException("云分析暂时失败（转写结果无法保存）", false);
    }
  }

  private void writeDrafts(CaptureSession session, LlmAdapter.Extraction extraction) {
    LlmAdapter.ProfileDraft draft = extraction.profile();
    CustomerProfile profile = profiles.findBySessionId(session.id).orElseGet(() -> {
      CustomerProfile created = new CustomerProfile();
      created.sessionId = session.id;
      created.confirmationStatus = ConfirmationStatus.draft;
      return created;
    });
    if (profile.confirmationStatus != ConfirmationStatus.confirmed) {
      profile.appellation = clip(draft.appellation(), 80);
      profile.intent = clip(draft.intent(), 200);
      profile.budget = clip(draft.budget(), 80);
      profile.decisionMaker = clip(draft.decisionMaker(), 80);
      profile.notes = clip(draft.notes(), 1000);
      profile.confirmationStatus = ConfirmationStatus.draft;
      profile.confirmedAt = null;
      profile.confirmedBy = null;
      profile.updatedAt = Instant.now();
      profiles.save(profile);
    }
    for (NeedItem item : needs.findBySessionIdOrderByIdAsc(session.id)) {
      if (item.origin == NeedOrigin.model && item.confirmationStatus != ConfirmationStatus.confirmed) {
        needs.delete(item);
      }
    }
    for (LlmAdapter.NeedDraft need : extraction.needs()) {
      if (need.title() == null || need.title().isBlank()) {
        continue;
      }
      NeedItem item = new NeedItem();
      item.sessionId = session.id;
      item.title = clip(need.title(), 120);
      item.detail = clip(need.detail(), 500);
      item.confirmationStatus = ConfirmationStatus.draft;
      item.followStatus = NeedFollowStatus.open;
      item.origin = NeedOrigin.model;
      item.ownerUserId = session.ownerUserId;
      item.confirmedAt = null;
      item.confirmedBy = null;
      item.updatedAt = Instant.now();
      needs.save(item);
    }
  }

  private void fail(AnalysisJob job, CaptureSession session, String message) {
    String safe = SecretRedactor.redact(message, properties.secretValues());
    if (safe == null || safe.isBlank()) {
      safe = "云分析暂时失败";
    }
    if (safe.length() > 180) {
      safe = safe.substring(0, 180);
    }
    job.status = JobStatus.failed;
    job.lastError = safe;
    job.updatedAt = Instant.now();
    session.transcriptStatus = TranscriptStatus.failed;
    session.failureReason = safe;
    session.updatedAt = Instant.now();
    jobs.save(job);
    sessions.save(session);
  }

  private AnalysisResponse toResponse(AnalysisJob job) {
    return toResponse(job, job == null ? null : job.sessionId);
  }

  private AnalysisResponse toResponse(AnalysisJob job, Long sessionId) {
    String ui = AnalysisCopy.uiStatus(job);
    String subtitle = AnalysisCopy.subtitle(ui);
    if (job != null && job.status == JobStatus.failed && job.lastError != null && !job.lastError.isBlank()) {
      subtitle = job.lastError;
    }
    return new AnalysisResponse(
        sessionId,
        job == null || job.status == null ? null : job.status.name(),
        job == null || job.stage == null ? null : job.stage.name(),
        ui,
        AnalysisCopy.title(ui),
        subtitle,
        job == null ? 0 : job.retryCount,
        AnalysisCopy.RETRY_LIMIT,
        AnalysisCopy.canRetry(job),
        AnalysisCopy.canConfirm(job),
        job == null ? null : job.lastError,
        job == null ? properties.getAsr().getProvider() : job.vendor);
  }

  private String noteFor(CaptureSession session, AnalysisJob job) {
    String ui = AnalysisCopy.uiStatus(job);
    if ("processing_llm".equals(ui) || (job != null && job.status == JobStatus.succeeded)) {
      return "mock".equalsIgnoreCase(engine())
          ? "当前为 Mock ASR，非云端转写。分句角色不做手动纠正。"
          : "火山引擎转写已完成。说话人显示为 spk0、spk1，不代表已识别真实身份。";
    }
    if (session.transcriptStatus == null) {
      return "转写尚未完成";
    }
    return switch (session.transcriptStatus) {
      case queued -> "转写尚未完成…";
      case processing -> "转写尚未完成…";
      case failed -> "暂无转写稿。失败原因：" + (session.failureReason == null ? "云分析暂时失败" : session.failureReason);
      case succeeded -> "mock".equalsIgnoreCase(engine())
          ? "当前为 Mock ASR，非云端转写。一期不能手动改正分句角色。"
          : "火山引擎转写已完成。说话人显示为 spk0、spk1，不代表已识别真实身份。";
    };
  }

  private String engine() {
    return properties.getAsr().getProvider() == null ? "mock" : properties.getAsr().getProvider();
  }

  private List<SentenceResponse> readSentences(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      List<AsrAdapter.AsrSentence> parsed = objectMapper.readValue(json, new TypeReference<>() {
      });
      return parsed.stream().map(sentence -> new SentenceResponse(sentence.speaker(), sentence.text())).toList();
    } catch (JsonProcessingException ex) {
      return List.of();
    }
  }

  private static String clip(String value, int max) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }
}
