package com.mobilebadge.web;

import com.mobilebadge.service.AuthService;
import com.mobilebadge.web.ApiModels.LoginRequest;
import com.mobilebadge.web.ApiModels.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/api/auth/login")
  public MeResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
    return authService.login(request, http, response);
  }

  @PostMapping("/api/auth/logout")
  public Map<String, String> logout(HttpServletRequest http) {
    authService.logout(http);
    return Map.of("status", "ok");
  }

  @GetMapping("/api/me")
  public MeResponse me() {
    return authService.me();
  }

  @GetMapping("/api/health")
  public Map<String, String> health() {
    return Map.of("status", "ok", "service", "mobile-badge-mgmt");
  }
}
