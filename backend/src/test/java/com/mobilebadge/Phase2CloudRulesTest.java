package com.mobilebadge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilebadge.adapt.MockAsrAdapter;
import com.mobilebadge.repo.WebhookPolicyRepository;
import com.mobilebadge.seed.DataSeeder;
import com.mobilebadge.service.AnalysisControl;
import com.mobilebadge.service.AnalysisService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase2CloudRulesTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  MockAsrAdapter mockAsr;

  @Autowired
  AnalysisControl analysisControl;

  @Autowired
  AnalysisService analysisService;

  @Autowired
  WebhookPolicyRepository webhookPolicies;

  @AfterEach
  void reset() {
    mockAsr.failNext(0);
    analysisControl.pauseBeforeLlm(false);
    webhookPolicies.deleteAll();
  }

  @Test
  void llmDraftIsNotConfirmedUntilHumanConfirmAndOtherAdvisorCannotReadIt() throws Exception {
    MockHttpSession advisor = advisor("p2a");
    MockHttpSession other = advisor("p2b");
    long id = importWav(advisor, "草案客户-" + System.nanoTime());
    mvc.perform(get("/api/sessions/" + id + "/analysis").session(advisor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uiStatus").value("succeeded"))
        .andExpect(jsonPath("$.canConfirm").value(true));
    mvc.perform(get("/api/sessions/" + id + "/profile").session(advisor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmationStatus").value("draft"));
    mvc.perform(get("/api/sessions/" + id + "/needs").session(advisor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].confirmationStatus").value("draft"));
    mvc.perform(get("/api/sessions/" + id + "/transcript").session(other)).andExpect(status().isForbidden());
    mvc.perform(get("/api/sessions/" + id + "/analysis").session(other)).andExpect(status().isForbidden());
    mvc.perform(post("/api/sessions/" + id + "/profile/confirm").session(advisor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmationStatus").value("confirmed"));
  }

  @Test
  void vendorFailureRetriesUntilLimitAndNeverConfirms() throws Exception {
    MockHttpSession advisor = advisor("p2r");
    mockAsr.failNext(10);
    long id = importWav(advisor, "失败客户");
    mvc.perform(get("/api/sessions/" + id + "/analysis").session(advisor))
        .andExpect(jsonPath("$.status").value("failed"))
        .andExpect(jsonPath("$.uiStatus").value("failed"))
        .andExpect(jsonPath("$.canRetry").value(true))
        .andExpect(jsonPath("$.retryLimit").value(3))
        .andExpect(jsonPath("$.lastError").value(org.hamcrest.Matchers.containsString("5xx")));
    mvc.perform(get("/api/sessions/" + id + "/profile").session(advisor))
        .andExpect(jsonPath("$.confirmationStatus").value(org.hamcrest.Matchers.nullValue()));
    for (int n = 1; n <= 3; n++) {
      mvc.perform(post("/api/sessions/" + id + "/analysis/retry").session(advisor))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.retryCount").value(n))
          .andExpect(jsonPath("$.status").value("failed"));
    }
    mvc.perform(post("/api/sessions/" + id + "/analysis/retry").session(advisor))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RETRY_LIMIT"));
    mvc.perform(post("/api/sessions/" + id + "/profile/confirm").session(advisor))
        .andExpect(status().isConflict());
  }

  @Test
  void retryCanSucceedAndProcessingLlmCannotConfirm() throws Exception {
    MockHttpSession advisor = advisor("p2s");
    mockAsr.failNext(1);
    long failed = importWav(advisor, "重试成功");
    mockAsr.failNext(0);
    mvc.perform(post("/api/sessions/" + failed + "/analysis/retry").session(advisor))
        .andExpect(jsonPath("$.status").value("succeeded"))
        .andExpect(jsonPath("$.retryCount").value(1));
    mvc.perform(get("/api/sessions/" + failed + "/profile").session(advisor))
        .andExpect(jsonPath("$.confirmationStatus").value("draft"));

    analysisControl.pauseBeforeLlm(true);
    long paused = importWav(advisor, "生成中");
    mvc.perform(get("/api/sessions/" + paused + "/analysis").session(advisor))
        .andExpect(jsonPath("$.uiStatus").value("processing_llm"))
        .andExpect(jsonPath("$.canConfirm").value(false))
        .andExpect(jsonPath("$.title").value("分析中 · 生成草案中（千问）"));
    mvc.perform(get("/api/sessions/" + paused + "/transcript").session(advisor))
        .andExpect(jsonPath("$.sentences[0].speaker").value("advisor"));
    mvc.perform(post("/api/sessions/" + paused + "/profile/confirm").session(advisor))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ANALYSIS_PENDING"));
    analysisControl.pauseBeforeLlm(false);
    analysisService.kick(paused);
    mvc.perform(get("/api/sessions/" + paused + "/analysis").session(advisor))
        .andExpect(jsonPath("$.uiStatus").value("succeeded"));
    mvc.perform(post("/api/sessions/" + paused + "/profile/confirm").session(advisor))
        .andExpect(jsonPath("$.confirmationStatus").value("confirmed"));
  }

  @Test
  void webhookAllowlistHttpsAndEmptyListStayLocal() throws Exception {
    MockHttpSession admin = login(DataSeeder.ADMIN_ACCOUNT, DataSeeder.ADMIN_PASSWORD);
    mvc.perform(post("/api/admin/webhooks").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("url", "http://example.com/hook", "secret", "secret-key-1", "events", java.util.List.of("profile.confirmed")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("禁止使用 http://，请改用 HTTPS。"));
    mvc.perform(put("/api/admin/webhooks/policy").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("allowlist", "hooks.allowed.test", "outboundEnabled", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outboundEnabled").value(true));
    mvc.perform(post("/api/admin/webhooks").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("url", "https://evil.example/hook", "secret", "secret-key-1", "events", java.util.List.of("profile.confirmed")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("白名单")));
    mvc.perform(put("/api/admin/webhooks/policy").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("allowlist", "", "outboundEnabled", true))))
        .andExpect(status().isOk());
    MvcResult created = mvc.perform(post("/api/admin/webhooks").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("url", "https://example.com/hook", "secret", "secret-key-1", "events", java.util.List.of("profile.confirmed")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notice").value(org.hamcrest.Matchers.containsString("不会出站")))
        .andReturn();
    long hookId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    mvc.perform(post("/api/admin/webhooks/" + hookId + "/test").session(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statusCode").value(0))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("未向外部")))
        .andExpect(jsonPath("$.state").value("skipped"));

    MockHttpSession advisor = advisor("p2w");
    long id = importWav(advisor, "出站客户");
    mvc.perform(post("/api/sessions/" + id + "/profile/confirm").session(advisor)).andExpect(status().isOk());
    String deliveries = mvc.perform(get("/api/admin/webhooks/deliveries").session(admin))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertTrue(deliveries.contains("未向外部"));
    assertFalse(deliveries.contains("secret-key-1"));
    assertFalse(deliveries.contains("VOLC_"));
  }

  @Test
  void consentTemplateNamesVendorsAndRecordingStillRequiresConsent() throws Exception {
    MockHttpSession admin = login(DataSeeder.ADMIN_ACCOUNT, DataSeeder.ADMIN_PASSWORD);
    mvc.perform(get("/api/admin/consent-policy").session(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blockWithoutConsent").value(true))
        .andExpect(jsonPath("$.statementTemplate").value(org.hamcrest.Matchers.containsString("火山引擎（ASR）")))
        .andExpect(jsonPath("$.statementTemplate").value(org.hamcrest.Matchers.containsString("阿里云百炼（LLM）")));
    mvc.perform(get("/api/health"))
        .andExpect(jsonPath("$.asrProvider").value("mock"))
        .andExpect(jsonPath("$.llmProvider").value("mock"))
        .andExpect(jsonPath("$.asrConfigured").value("true"))
        .andExpect(jsonPath("$.apiKey").doesNotExist())
        .andExpect(jsonPath("$.accessKey").doesNotExist());
    mvc.perform(post("/api/internal/asr/callback").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());

    MockHttpSession advisor = advisor("p2c");
    MvcResult created = mvc.perform(post("/api/sessions").session(advisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("source", "phone", "customerLabel", "未授权"))))
        .andExpect(status().isOk())
        .andReturn();
    long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    mvc.perform(post("/api/sessions/" + id + "/recording/start").session(advisor))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NO_CONSENT"));
    mvc.perform(post("/api/sessions").session(advisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("source", "badge"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BADGE_NOT_AVAILABLE"));
  }

  private MockHttpSession advisor(String prefix) throws Exception {
    MockHttpSession admin = login(DataSeeder.ADMIN_ACCOUNT, DataSeeder.ADMIN_PASSWORD);
    String account = prefix + System.nanoTime();
    mvc.perform(post("/api/members").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("account", account, "name", "顾问", "password", "Advisor123", "role", "ADVISOR"))))
        .andExpect(status().isOk());
    return login(account, "Advisor123");
  }

  private long importWav(MockHttpSession session, String label) throws Exception {
    MockMultipartFile wav = new MockMultipartFile("file", label + ".wav", "audio/wav", new byte[] {1, 2, 3, 4});
    MvcResult result = mvc.perform(multipart("/api/sessions/import")
            .file(wav)
            .param("customerLabel", label)
            .param("consentAcknowledged", "true")
            .session(session))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  private MockHttpSession login(String account, String password) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("account", account, "password", password))))
        .andExpect(status().isOk())
        .andReturn();
    MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
    assertNotNull(session);
    return session;
  }

  private String json(Object body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }
}
