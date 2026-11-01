package com.mobilebadge.web;

import com.mobilebadge.domain.CaptureStatus;
import com.mobilebadge.domain.DeviceSource;
import com.mobilebadge.domain.ExportFormat;
import com.mobilebadge.domain.TranscriptStatus;
import com.mobilebadge.security.SessionAccess;
import com.mobilebadge.security.SessionAccessMode;
import com.mobilebadge.service.CaptureService;
import com.mobilebadge.service.ExportService;
import com.mobilebadge.service.OverviewService;
import com.mobilebadge.service.WebhookService;
import com.mobilebadge.web.ApiModels.CapabilityRow;
import com.mobilebadge.web.ApiModels.ExportCreate;
import com.mobilebadge.web.ApiModels.ExportJobResponse;
import com.mobilebadge.web.ApiModels.OverviewResponse;
import com.mobilebadge.web.ApiModels.PageResponse;
import com.mobilebadge.web.ApiModels.SessionResponse;
import com.mobilebadge.web.ApiModels.WebhookCreate;
import com.mobilebadge.web.ApiModels.WebhookDeliveryResponse;
import com.mobilebadge.web.ApiModels.WebhookResponse;
import com.mobilebadge.web.ApiModels.WebhookUpdate;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

  private final OverviewService overviewService;
  private final CaptureService captureService;
  private final ExportService exportService;
  private final WebhookService webhookService;

  public AdminController(
      OverviewService overviewService,
      CaptureService captureService,
      ExportService exportService,
      WebhookService webhookService) {
    this.overviewService = overviewService;
    this.captureService = captureService;
    this.exportService = exportService;
    this.webhookService = webhookService;
  }

  @GetMapping("/api/admin/overview")
  @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
  public OverviewResponse overview() {
    return overviewService.overview();
  }

  @GetMapping("/api/admin/roles")
  @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
  public List<CapabilityRow> roles() {
    return overviewService.roles();
  }

  @GetMapping("/api/admin/sessions")
  @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
  public PageResponse<SessionResponse> sessions(
      @RequestParam(required = false) DeviceSource source,
      @RequestParam(required = false) TranscriptStatus transcriptStatus,
      @RequestParam(required = false) CaptureStatus captureStatus,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return captureService.listOrg(source, transcriptStatus, captureStatus, page, size);
  }

  @GetMapping("/api/admin/sessions/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
  @SessionAccess(SessionAccessMode.READ)
  public SessionResponse session(@PathVariable long id) {
    return captureService.get(id);
  }

  @PostMapping("/api/admin/exports")
  @PreAuthorize("hasRole('ADMIN')")
  public ExportJobResponse export(@Valid @RequestBody ExportCreate request) {
    return exportService.create(request);
  }

  @GetMapping("/api/admin/exports")
  @PreAuthorize("hasRole('ADMIN')")
  public List<ExportJobResponse> exports() {
    return exportService.list();
  }

  @GetMapping("/api/admin/exports/{id}/download")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Resource> download(@PathVariable Long id) {
    var job = exportService.require(id);
    String filename = job.format == ExportFormat.CSV ? "confirmed-export.csv" : "confirmed-export.json";
    MediaType media = job.format == ExportFormat.CSV
        ? MediaType.parseMediaType("text/csv")
        : MediaType.APPLICATION_JSON;
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(media)
        .body(exportService.download(id));
  }

  @GetMapping("/api/admin/webhooks")
  @PreAuthorize("hasRole('ADMIN')")
  public List<WebhookResponse> webhooks() {
    return webhookService.list();
  }

  @PostMapping("/api/admin/webhooks")
  @PreAuthorize("hasRole('ADMIN')")
  public WebhookResponse createWebhook(@Valid @RequestBody WebhookCreate request) {
    return webhookService.create(request);
  }

  @PatchMapping("/api/admin/webhooks/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public WebhookResponse updateWebhook(@PathVariable Long id, @RequestBody WebhookUpdate request) {
    return webhookService.update(id, request);
  }

  @PostMapping("/api/admin/webhooks/{id}/test")
  @PreAuthorize("hasRole('ADMIN')")
  public WebhookDeliveryResponse testWebhook(@PathVariable Long id) {
    return webhookService.test(id);
  }

  @GetMapping("/api/admin/webhooks/deliveries")
  @PreAuthorize("hasRole('ADMIN')")
  public List<WebhookDeliveryResponse> deliveries() {
    return webhookService.deliveries();
  }
}
