package com.mobilebadge.web;

import com.mobilebadge.service.MemberService;
import com.mobilebadge.service.OrgService;
import com.mobilebadge.web.ApiModels.MemberCreate;
import com.mobilebadge.web.ApiModels.MemberResponse;
import com.mobilebadge.web.ApiModels.MemberUpdate;
import com.mobilebadge.web.ApiModels.OrgResponse;
import com.mobilebadge.web.ApiModels.OrgUpdate;
import com.mobilebadge.web.ApiModels.StoreResponse;
import com.mobilebadge.web.ApiModels.StoreWrite;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrgMemberController {

  private final OrgService orgService;
  private final MemberService memberService;

  public OrgMemberController(OrgService orgService, MemberService memberService) {
    this.orgService = orgService;
    this.memberService = memberService;
  }

  @GetMapping("/api/org")
  public OrgResponse org() {
    return orgService.current();
  }

  @PutMapping("/api/org")
  @PreAuthorize("hasRole('ADMIN')")
  public OrgResponse rename(@Valid @RequestBody OrgUpdate request) {
    return orgService.rename(request.name());
  }

  @GetMapping("/api/stores")
  public List<StoreResponse> stores() {
    return orgService.listStores();
  }

  @PostMapping("/api/stores")
  @PreAuthorize("hasRole('ADMIN')")
  public StoreResponse createStore(@Valid @RequestBody StoreWrite request) {
    return orgService.createStore(request.name());
  }

  @PatchMapping("/api/stores/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public StoreResponse updateStore(@PathVariable Long id, @Valid @RequestBody StoreWrite request) {
    return orgService.updateStore(id, request.name(), request.enabled());
  }

  @GetMapping("/api/members")
  @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
  public List<MemberResponse> members() {
    return memberService.list();
  }

  @PostMapping("/api/members")
  @PreAuthorize("hasRole('ADMIN')")
  public MemberResponse createMember(@Valid @RequestBody MemberCreate request) {
    return memberService.create(request);
  }

  @PatchMapping("/api/members/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public MemberResponse updateMember(@PathVariable Long id, @RequestBody MemberUpdate request) {
    return memberService.update(id, request);
  }
}
