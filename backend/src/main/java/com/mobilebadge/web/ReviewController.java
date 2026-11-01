package com.mobilebadge.web;

import com.mobilebadge.domain.ConfirmationStatus;
import com.mobilebadge.security.SessionAccess;
import com.mobilebadge.security.SessionAccessMode;
import com.mobilebadge.service.NeedService;
import com.mobilebadge.service.ProfileService;
import com.mobilebadge.web.ApiModels.FollowWrite;
import com.mobilebadge.web.ApiModels.NeedResponse;
import com.mobilebadge.web.ApiModels.NeedWrite;
import com.mobilebadge.web.ApiModels.ProfileResponse;
import com.mobilebadge.web.ApiModels.ProfileWrite;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewController {

  private final ProfileService profileService;
  private final NeedService needService;

  public ReviewController(ProfileService profileService, NeedService needService) {
    this.profileService = profileService;
    this.needService = needService;
  }

  @GetMapping("/api/sessions/{id}/profile")
  @SessionAccess(SessionAccessMode.READ)
  public ProfileResponse profile(@PathVariable long id) {
    return profileService.get(id);
  }

  @PutMapping("/api/sessions/{id}/profile")
  @SessionAccess(SessionAccessMode.OWNER)
  public ProfileResponse saveProfile(@PathVariable long id, @RequestBody ProfileWrite request) {
    return profileService.save(id, request);
  }

  @PostMapping("/api/sessions/{id}/profile/generate")
  @SessionAccess(SessionAccessMode.OWNER)
  public ProfileResponse generateProfile(@PathVariable long id) {
    return profileService.generate(id);
  }

  @PostMapping("/api/sessions/{id}/profile/confirm")
  @SessionAccess(SessionAccessMode.OWNER)
  public ProfileResponse confirmProfile(@PathVariable long id) {
    return profileService.confirm(id);
  }

  @PostMapping("/api/sessions/{id}/profile/unlock")
  @SessionAccess(SessionAccessMode.OWNER)
  public ProfileResponse unlockProfile(@PathVariable long id) {
    return profileService.unlock(id);
  }

  @GetMapping("/api/sessions/{id}/needs")
  @SessionAccess(SessionAccessMode.READ)
  public List<NeedResponse> needs(@PathVariable long id, @RequestParam(required = false) ConfirmationStatus status) {
    return needService.list(id, status);
  }

  @PostMapping("/api/sessions/{id}/needs")
  @SessionAccess(SessionAccessMode.OWNER)
  public NeedResponse createNeed(@PathVariable long id, @Valid @RequestBody NeedWrite request) {
    return needService.create(id, request);
  }

  @PutMapping("/api/sessions/{id}/needs/{needId}")
  @SessionAccess(SessionAccessMode.OWNER)
  public NeedResponse updateNeed(@PathVariable long id, @PathVariable long needId, @Valid @RequestBody NeedWrite request) {
    return needService.update(id, needId, request);
  }

  @PostMapping("/api/sessions/{id}/needs/generate")
  @SessionAccess(SessionAccessMode.OWNER)
  public List<NeedResponse> generateNeeds(@PathVariable long id) {
    return needService.generate(id);
  }

  @PostMapping("/api/sessions/{id}/needs/{needId}/confirm")
  @SessionAccess(SessionAccessMode.OWNER)
  public NeedResponse confirmNeed(@PathVariable long id, @PathVariable long needId) {
    return needService.confirm(id, needId);
  }

  @PostMapping("/api/sessions/{id}/needs/{needId}/unlock")
  @SessionAccess(SessionAccessMode.OWNER)
  public NeedResponse unlockNeed(@PathVariable long id, @PathVariable long needId) {
    return needService.unlock(id, needId);
  }

  @PostMapping("/api/sessions/{id}/needs/{needId}/discard")
  @SessionAccess(SessionAccessMode.OWNER)
  public NeedResponse discardNeed(@PathVariable long id, @PathVariable long needId) {
    return needService.discard(id, needId);
  }

  @PatchMapping("/api/sessions/{id}/needs/{needId}/follow")
  @SessionAccess(SessionAccessMode.OWNER)
  public NeedResponse follow(@PathVariable long id, @PathVariable long needId, @Valid @RequestBody FollowWrite request) {
    return needService.follow(id, needId, request.followStatus());
  }

  @GetMapping("/api/confirmed/profiles")
  public List<ProfileResponse> confirmedProfiles(@RequestParam(required = false) String q) {
    return profileService.confirmed(q);
  }

  @GetMapping("/api/confirmed/needs")
  public List<NeedResponse> confirmedNeeds(@RequestParam(required = false) String q) {
    return needService.confirmed(q);
  }
}
