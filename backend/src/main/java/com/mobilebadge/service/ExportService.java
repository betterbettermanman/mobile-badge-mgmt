package com.mobilebadge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilebadge.adapt.ObjectStorage;
import com.mobilebadge.common.ApiException;
import com.mobilebadge.domain.CaptureSession;
import com.mobilebadge.domain.ConfirmationStatus;
import com.mobilebadge.domain.CustomerProfile;
import com.mobilebadge.domain.ExportFormat;
import com.mobilebadge.domain.ExportJob;
import com.mobilebadge.domain.JobStatus;
import com.mobilebadge.domain.NeedItem;
import com.mobilebadge.domain.Transcript;
import com.mobilebadge.repo.CaptureSessionRepository;
import com.mobilebadge.repo.CustomerProfileRepository;
import com.mobilebadge.repo.ExportJobRepository;
import com.mobilebadge.repo.NeedItemRepository;
import com.mobilebadge.repo.TranscriptRepository;
import com.mobilebadge.security.AuthPrincipal;
import com.mobilebadge.security.CurrentUser;
import com.mobilebadge.web.ApiModels.ExportCreate;
import com.mobilebadge.web.ApiModels.ExportJobResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportService {

  private final ExportJobRepository jobs;
  private final CustomerProfileRepository profiles;
  private final NeedItemRepository needs;
  private final CaptureSessionRepository sessions;
  private final TranscriptRepository transcripts;
  private final ObjectStorage storage;
  private final ObjectMapper objectMapper;

  public ExportService(
      ExportJobRepository jobs,
      CustomerProfileRepository profiles,
      NeedItemRepository needs,
      CaptureSessionRepository sessions,
      TranscriptRepository transcripts,
      ObjectStorage storage,
      ObjectMapper objectMapper) {
    this.jobs = jobs;
    this.profiles = profiles;
    this.needs = needs;
    this.sessions = sessions;
    this.transcripts = transcripts;
    this.storage = storage;
    this.objectMapper = objectMapper;
  }

  public List<ExportJobResponse> list() {
    AuthPrincipal me = CurrentUser.require();
    return jobs.findByOrganizationIdOrderByIdDesc(me.organizationId()).stream().map(this::toResponse).toList();
  }

  @Transactional
  public ExportJobResponse create(ExportCreate request) {
    if (request.from() != null && request.to() != null && request.to().isBefore(request.from())) {
      throw ApiException.badRequest("RANGE", "结束时间需不早于开始时间");
    }
    AuthPrincipal me = CurrentUser.require();
    boolean includeProfiles = request.includeConfirmedProfiles() == null || request.includeConfirmedProfiles();
    boolean includeNeeds = request.includeConfirmedNeeds() == null || request.includeConfirmedNeeds();
    boolean includeMeta = Boolean.TRUE.equals(request.includeSessionMeta());
    boolean includeTranscript = Boolean.TRUE.equals(request.includeTranscript());

    ExportJob job = new ExportJob();
    job.organizationId = me.organizationId();
    job.createdBy = me.userId();
    job.format = request.format();
    job.status = JobStatus.processing;
    job.fromDate = request.from();
    job.toDate = request.to();
    job.includeConfirmedProfiles = includeProfiles;
    job.includeConfirmedNeeds = includeNeeds;
    job.includeSessionMeta = includeMeta;
    job.includeTranscript = includeTranscript;
    job.message = "默认仅导出已确认数据，不包含草案";
    jobs.save(job);

    try {
      byte[] body = request.format() == ExportFormat.CSV
          ? csv(me, job).getBytes(StandardCharsets.UTF_8)
          : objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload(me, job));
      String ext = request.format() == ExportFormat.CSV ? "csv" : "json";
      String key = "exports/" + job.id + "." + ext;
      storage.storeBytes(key, body);
      job.storageKey = key;
      job.status = JobStatus.succeeded;
    } catch (Exception ex) {
      job.status = JobStatus.failed;
      job.message = "导出失败";
    }
    jobs.save(job);
    return toResponse(job);
  }

  public Resource download(Long id) {
    AuthPrincipal me = CurrentUser.require();
    ExportJob job = jobs.findById(id).orElseThrow(ApiException::notFound);
    if (!job.organizationId.equals(me.organizationId()) || job.status != JobStatus.succeeded || job.storageKey == null) {
      throw ApiException.notFound();
    }
    return new FileSystemResource(storage.resolve(job.storageKey));
  }

  public ExportJob require(Long id) {
    AuthPrincipal me = CurrentUser.require();
    ExportJob job = jobs.findById(id).orElseThrow(ApiException::notFound);
    if (!job.organizationId.equals(me.organizationId())) {
      throw ApiException.forbidden();
    }
    return job;
  }

  private Map<String, Object> payload(AuthPrincipal me, ExportJob job) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("note", "默认仅导出已确认数据，不包含草案");
    body.put("generatedAt", Instant.now().toString());
    if (job.includeConfirmedProfiles) {
      body.put("profiles", confirmedProfiles(me, job));
    }
    if (job.includeConfirmedNeeds) {
      body.put("needs", confirmedNeeds(me, job));
    }
    if (job.includeSessionMeta) {
      body.put("sessions", sessionMeta(me, job));
    }
    if (job.includeTranscript) {
      body.put("transcripts", transcriptRows(me, job));
    }
    return body;
  }

  private String csv(AuthPrincipal me, ExportJob job) {
    StringBuilder builder = new StringBuilder();
    builder.append("type,id,sessionId,title,intent,budget,decisionMaker,notes,followStatus,confirmedAt\n");
    if (job.includeConfirmedProfiles) {
      for (Map<String, Object> row : confirmedProfiles(me, job)) {
        builder.append(csvRow("profile", row.get("id"), row.get("sessionId"), row.get("appellation"),
            row.get("intent"), row.get("budget"), row.get("decisionMaker"), row.get("notes"), "", row.get("confirmedAt")));
      }
    }
    if (job.includeConfirmedNeeds) {
      for (Map<String, Object> row : confirmedNeeds(me, job)) {
        builder.append(csvRow("need", row.get("id"), row.get("sessionId"), row.get("title"),
            "", "", "", row.get("detail"), row.get("followStatus"), row.get("confirmedAt")));
      }
    }
    return builder.toString();
  }

  private List<Map<String, Object>> confirmedProfiles(AuthPrincipal me, ExportJob job) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (CustomerProfile profile : profiles.findByConfirmationStatus(ConfirmationStatus.confirmed)) {
      CaptureSession session = sessions.findById(profile.sessionId).orElse(null);
      if (!inRange(session, me, job)) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", profile.id);
      row.put("sessionId", profile.sessionId);
      row.put("appellation", profile.appellation);
      row.put("intent", profile.intent);
      row.put("budget", profile.budget);
      row.put("decisionMaker", profile.decisionMaker);
      row.put("notes", profile.notes);
      row.put("confirmedAt", profile.confirmedAt);
      rows.add(row);
    }
    return rows;
  }

  private List<Map<String, Object>> confirmedNeeds(AuthPrincipal me, ExportJob job) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (NeedItem item : needs.findByConfirmationStatus(ConfirmationStatus.confirmed)) {
      CaptureSession session = sessions.findById(item.sessionId).orElse(null);
      if (!inRange(session, me, job)) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", item.id);
      row.put("sessionId", item.sessionId);
      row.put("title", item.title);
      row.put("detail", item.detail);
      row.put("followStatus", item.followStatus);
      row.put("confirmedAt", item.confirmedAt);
      rows.add(row);
    }
    return rows;
  }

  private List<Map<String, Object>> sessionMeta(AuthPrincipal me, ExportJob job) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (CaptureSession session : sessions.findByOrganizationId(me.organizationId())) {
      if (!inRange(session, me, job)) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", session.id);
      row.put("customerLabel", session.customerLabel);
      row.put("source", session.source);
      row.put("ownerUserId", session.ownerUserId);
      row.put("captureStatus", session.captureStatus);
      row.put("transcriptStatus", session.transcriptStatus);
      row.put("createdAt", session.createdAt);
      rows.add(row);
    }
    return rows;
  }

  private List<Map<String, Object>> transcriptRows(AuthPrincipal me, ExportJob job) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (CaptureSession session : sessions.findByOrganizationId(me.organizationId())) {
      if (!inRange(session, me, job) || session.transcriptStatus != com.mobilebadge.domain.TranscriptStatus.succeeded) {
        continue;
      }
      Transcript transcript = transcripts.findBySessionId(session.id).orElse(null);
      if (transcript == null) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("sessionId", session.id);
      row.put("fullText", transcript.fullText);
      rows.add(row);
    }
    return rows;
  }

  private boolean inRange(CaptureSession session, AuthPrincipal me, ExportJob job) {
    if (session == null || !session.organizationId.equals(me.organizationId())) {
      return false;
    }
    LocalDate day = LocalDate.ofInstant(session.createdAt, ZoneOffset.UTC);
    if (job.fromDate != null && day.isBefore(job.fromDate)) {
      return false;
    }
    return job.toDate == null || !day.isAfter(job.toDate);
  }

  private ExportJobResponse toResponse(ExportJob job) {
    String path = job.status == JobStatus.succeeded ? "/api/admin/exports/" + job.id + "/download" : null;
    return new ExportJobResponse(
        job.id,
        job.format,
        job.status,
        job.fromDate,
        job.toDate,
        job.includeConfirmedProfiles,
        job.includeConfirmedNeeds,
        job.includeSessionMeta,
        job.includeTranscript,
        job.message,
        path,
        job.createdAt);
  }

  private static String csvRow(Object... cells) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < cells.length; i++) {
      if (i > 0) {
        builder.append(',');
      }
      builder.append(escape(cells[i]));
    }
    builder.append('\n');
    return builder.toString();
  }

  private static String escape(Object value) {
    if (value == null) {
      return "";
    }
    String text = String.valueOf(value).replace("\"", "\"\"");
    if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
      return "\"" + text + "\"";
    }
    return text;
  }
}
