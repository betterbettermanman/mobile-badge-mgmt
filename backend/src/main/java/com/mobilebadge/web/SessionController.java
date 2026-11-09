package com.mobilebadge.web;

import com.mobilebadge.domain.CaptureStatus;
import com.mobilebadge.domain.DeviceSource;
import com.mobilebadge.domain.TranscriptStatus;
import com.mobilebadge.security.SessionAccess;
import com.mobilebadge.security.SessionAccessMode;
import com.mobilebadge.service.AnalysisService;
import com.mobilebadge.service.CaptureService;
import com.mobilebadge.web.ApiModels.AnalysisResponse;
import com.mobilebadge.web.ApiModels.PageResponse;
import com.mobilebadge.web.ApiModels.SessionCreate;
import com.mobilebadge.web.ApiModels.SessionResponse;
import com.mobilebadge.web.ApiModels.TranscriptResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class SessionController {

  private final CaptureService captureService;
  private final AnalysisService analysisService;

  public SessionController(CaptureService captureService, AnalysisService analysisService) {
    this.captureService = captureService;
    this.analysisService = analysisService;
  }

  @GetMapping("/api/sessions")
  public PageResponse<SessionResponse> mine(
      @RequestParam(required = false) DeviceSource source,
      @RequestParam(required = false) TranscriptStatus transcriptStatus,
      @RequestParam(required = false) CaptureStatus captureStatus,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return captureService.listMine(source, transcriptStatus, captureStatus, page, size);
  }

  @PostMapping("/api/sessions")
  public SessionResponse create(@Valid @RequestBody SessionCreate request) {
    return captureService.create(request.customerLabel(), request.storeId(), request.source());
  }

  @PostMapping(value = "/api/sessions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public SessionResponse importFile(
      @RequestParam(required = false) String customerLabel,
      @RequestParam(required = false) Long storeId,
      @RequestParam(defaultValue = "false") boolean consentAcknowledged,
      @RequestPart("file") MultipartFile file) {
    return captureService.importFile(customerLabel, storeId, consentAcknowledged, file);
  }

  @GetMapping("/api/sessions/{id}")
  @SessionAccess(SessionAccessMode.READ)
  public SessionResponse get(@PathVariable long id) {
    return captureService.get(id);
  }

  @PostMapping("/api/sessions/{id}/recording/start")
  @SessionAccess(SessionAccessMode.OWNER)
  public SessionResponse start(@PathVariable long id) {
    return captureService.start(id);
  }

  @PostMapping("/api/sessions/{id}/recording/stop")
  @SessionAccess(SessionAccessMode.OWNER)
  public SessionResponse stop(@PathVariable long id) {
    return captureService.stop(id);
  }

  @PostMapping("/api/sessions/{id}/recording/cancel")
  @SessionAccess(SessionAccessMode.OWNER)
  public SessionResponse cancel(@PathVariable long id) {
    return captureService.cancel(id);
  }

  @PostMapping("/api/sessions/{id}/upload/retry")
  @SessionAccess(SessionAccessMode.OWNER)
  public SessionResponse retryUpload(@PathVariable long id) {
    return captureService.retryUpload(id);
  }

  @PostMapping("/api/sessions/{id}/transcript/rerun")
  @SessionAccess(SessionAccessMode.OWNER)
  public SessionResponse rerun(@PathVariable long id) {
    return captureService.rerun(id);
  }

  @GetMapping("/api/sessions/{id}/transcript")
  @SessionAccess(SessionAccessMode.READ)
  public TranscriptResponse transcript(@PathVariable long id) {
    return captureService.transcript(id);
  }

  @GetMapping("/api/sessions/{id}/analysis")
  @SessionAccess(SessionAccessMode.READ)
  public AnalysisResponse analysis(@PathVariable long id) {
    return analysisService.view(id);
  }

  @PostMapping("/api/sessions/{id}/analysis/retry")
  @SessionAccess(SessionAccessMode.OWNER)
  public AnalysisResponse retryAnalysis(@PathVariable long id) {
    return analysisService.retry(id);
  }
}
