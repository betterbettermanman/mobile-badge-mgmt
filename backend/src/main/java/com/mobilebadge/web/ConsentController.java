package com.mobilebadge.web;

import com.mobilebadge.service.ConsentService;
import com.mobilebadge.web.ApiModels.ConsentCreate;
import com.mobilebadge.web.ApiModels.ConsentRecordResponse;
import com.mobilebadge.web.ApiModels.ConsentStatusResponse;
import com.mobilebadge.web.ApiModels.PolicyResponse;
import com.mobilebadge.web.ApiModels.PolicyUpdate;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConsentController {

  private final ConsentService consentService;

  public ConsentController(ConsentService consentService) {
    this.consentService = consentService;
  }

  @GetMapping("/api/consents/status")
  public ConsentStatusResponse status() {
    return consentService.statusForCurrentUser();
  }

  @GetMapping("/api/consents")
  public List<ConsentRecordResponse> history() {
    return consentService.history();
  }

  @PostMapping("/api/consents")
  public ConsentStatusResponse acknowledge(@Valid @RequestBody ConsentCreate request) {
    return consentService.acknowledge(request.acknowledged(), request.sessionId());
  }

  @PostMapping("/api/consents/{id}/revoke")
  public ConsentStatusResponse revoke(@PathVariable Long id) {
    return consentService.revoke(id);
  }

  @GetMapping("/api/admin/consent-policy")
  @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
  public PolicyResponse policy() {
    return consentService.policy();
  }

  @PutMapping("/api/admin/consent-policy")
  @PreAuthorize("hasRole('ADMIN')")
  public PolicyResponse updatePolicy(@Valid @RequestBody PolicyUpdate request) {
    return consentService.updateValidity(request.validityDays());
  }
}
