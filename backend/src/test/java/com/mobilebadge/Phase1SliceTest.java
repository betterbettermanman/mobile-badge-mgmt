package com.mobilebadge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilebadge.seed.DataSeeder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
class Phase1SliceTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper objectMapper;

  @Test
  void loginLogoutAndWrongPassword() throws Exception {
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("account", DataSeeder.ADMIN_ACCOUNT, "password", "nope-pass"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("账号或密码不正确"));

    MockHttpSession session = login(DataSeeder.ADMIN_ACCOUNT, DataSeeder.ADMIN_PASSWORD);
    mvc.perform(get("/api/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.organizationName").value("体验中心"));

    mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isOk());
    mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void rolesAndHorizontalAccess() throws Exception {
    MockHttpSession admin = login(DataSeeder.ADMIN_ACCOUNT, DataSeeder.ADMIN_PASSWORD);
    MockHttpSession supervisor = login(DataSeeder.SUPERVISOR_ACCOUNT, DataSeeder.SUPERVISOR_PASSWORD);
    MockHttpSession advisor = login(DataSeeder.ADVISOR_ACCOUNT, DataSeeder.ADVISOR_PASSWORD);

    String otherAccount = "adv" + System.nanoTime();
    mvc.perform(post("/api/members").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "account", otherAccount,
                "name", "顾问乙",
                "password", "Advisor123",
                "role", "ADVISOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADVISOR"));

    mvc.perform(post("/api/members").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("account", "ab", "name", "短", "password", "123", "role", "ADVISOR"))))
        .andExpect(status().isBadRequest());

    MvcResult created = mvc.perform(post("/api/sessions").session(advisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("customerLabel", "甲的客户", "source", "phone"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("phone"))
        .andReturn();
    long sessionId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

    MockHttpSession other = login(otherAccount, "Advisor123");
    mvc.perform(get("/api/sessions/" + sessionId).session(other))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("无权访问该页面"));
    mvc.perform(post("/api/sessions/" + sessionId + "/profile/confirm").session(other))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/sessions/" + sessionId + "/profile/confirm").session(supervisor))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/sessions/" + sessionId + "/profile/confirm").session(admin))
        .andExpect(status().isForbidden());

    mvc.perform(get("/api/admin/sessions/" + sessionId).session(supervisor)).andExpect(status().isOk());
    mvc.perform(get("/api/admin/sessions").session(advisor)).andExpect(status().isForbidden());
    mvc.perform(get("/api/members").session(advisor)).andExpect(status().isForbidden());
    mvc.perform(get("/api/members").session(supervisor)).andExpect(status().isOk());
    mvc.perform(put("/api/org").session(supervisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("name", "不该改"))))
        .andExpect(status().isForbidden());

    mvc.perform(get("/api/me").session(other)).andExpect(jsonPath("$.account").value(otherAccount));
  }

  @Test
  void supervisorCannotInvitePolicyWebhookOrExport() throws Exception {
    MockHttpSession supervisor = login(DataSeeder.SUPERVISOR_ACCOUNT, DataSeeder.SUPERVISOR_PASSWORD);
    mvc.perform(post("/api/members").session(supervisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("account", "supx" + System.nanoTime(), "name", "越权", "password", "Advisor123", "role", "ADVISOR"))))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/admin/consent-policy").session(supervisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("validityDays", 10))))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/admin/webhooks").session(supervisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("url", "https://example.com/hook", "secret", "secret-key", "events", java.util.List.of("profile.confirmed")))))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/admin/exports").session(supervisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("format", "JSON"))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/exports").session(supervisor)).andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/devices").session(supervisor)).andExpect(status().isOk());
    mvc.perform(get("/api/admin/consent-policy").session(supervisor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blockWithoutConsent").value(true))
        .andExpect(jsonPath("$.remindOnlyAvailable").value(false));
  }

  @Test
  void badgeRejectedAndPhoneFileAccepted() throws Exception {
    MockHttpSession advisor = login(DataSeeder.ADVISOR_ACCOUNT, DataSeeder.ADVISOR_PASSWORD);
    mvc.perform(post("/api/sessions").session(advisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("source", "badge"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BADGE_NOT_AVAILABLE"));
    mvc.perform(post("/api/sessions").session(advisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("source", "badgeDeviceId"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BADGE_NOT_AVAILABLE"));
    mvc.perform(post("/api/devices/bind").session(advisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("source", "badge", "displayName", "工牌"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BADGE_NOT_AVAILABLE"));
    mvc.perform(post("/api/sessions").session(advisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("source", "file", "customerLabel", "文件占位"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("file"));
    mvc.perform(get("/api/meta/device-sources").session(advisor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("phone"))
        .andExpect(jsonPath("$[0].available").value(true))
        .andExpect(jsonPath("$[2].code").value("badge"))
        .andExpect(jsonPath("$[2].available").value(false));
    mvc.perform(patch("/api/sessions/1/transcript/sentences/0").session(advisor)).andExpect(status().isNotFound());
    mvc.perform(get("/api/sop").session(advisor)).andExpect(status().isNotFound());
    mvc.perform(get("/api/crm").session(advisor)).andExpect(status().isNotFound());
    mvc.perform(get("/api/ota").session(advisor)).andExpect(status().isNotFound());
  }

  @Test
  void recordingBlockedWithoutConsentThenAllowed() throws Exception {
    MockHttpSession admin = login(DataSeeder.ADMIN_ACCOUNT, DataSeeder.ADMIN_PASSWORD);
    String account = "mic" + System.nanoTime();
    mvc.perform(post("/api/members").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("account", account, "name", "录音顾问", "password", "Advisor123", "role", "ADVISOR"))))
        .andExpect(status().isOk());
    MockHttpSession advisor = login(account, "Advisor123");
    MvcResult created = mvc.perform(post("/api/sessions").session(advisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("source", "phone", "customerLabel", "授权测试"))))
        .andExpect(status().isOk())
        .andReturn();
    long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

    mvc.perform(post("/api/sessions/" + id + "/recording/start").session(advisor))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NO_CONSENT"))
        .andExpect(jsonPath("$.message").value("未取得录音授权，无法开始录音"));

    mvc.perform(post("/api/consents").session(advisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("acknowledged", false))))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/consents").session(advisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("acknowledged", true, "sessionId", id))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("valid"));

    mvc.perform(post("/api/sessions/" + id + "/recording/start").session(advisor))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEVICE_UNBOUND"));

    mvc.perform(post("/api/devices/bind").session(advisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("source", "phone", "displayName", "本机"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("phone"))
        .andExpect(jsonPath("$.status").value("bound"));

    mvc.perform(post("/api/sessions/" + id + "/recording/start").session(advisor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.captureStatus").value("recording"));
    mvc.perform(post("/api/sessions/" + id + "/recording/stop").session(advisor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.captureStatus").value("uploaded"))
        .andExpect(jsonPath("$.transcriptStatus").value("succeeded"));
    mvc.perform(get("/api/sessions/" + id + "/transcript").session(advisor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.engine").value("mock"))
        .andExpect(jsonPath("$.sentences[0].speaker").value("advisor"));
  }

  @Test
  void importRequiresConsentAndRejectsBadType() throws Exception {
    MockHttpSession admin = login(DataSeeder.ADMIN_ACCOUNT, DataSeeder.ADMIN_PASSWORD);
    String account = "file" + System.nanoTime();
    mvc.perform(post("/api/members").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("account", account, "name", "导入顾问", "password", "Advisor123", "role", "ADVISOR"))))
        .andExpect(status().isOk());
    MockHttpSession advisor = login(account, "Advisor123");
    MockMultipartFile wav = new MockMultipartFile("file", "talk.wav", "audio/wav", new byte[]{1, 2, 3, 4});
    mvc.perform(multipart("/api/sessions/import").file(wav).param("consentAcknowledged", "false").session(advisor))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NO_CONSENT"));
    MockMultipartFile txt = new MockMultipartFile("file", "note.txt", "text/plain", "hi".getBytes(StandardCharsets.UTF_8));
    mvc.perform(multipart("/api/sessions/import").file(txt).param("consentAcknowledged", "true").session(advisor))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FILE_TYPE"));
    mvc.perform(multipart("/api/sessions/import").file(wav).param("customerLabel", "导入客户").param("consentAcknowledged", "true").session(advisor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("file"))
        .andExpect(jsonPath("$.transcriptStatus").value("succeeded"));
  }

  @Test
  void draftsStayOutOfConfirmedAndExport() throws Exception {
    MockHttpSession admin = login(DataSeeder.ADMIN_ACCOUNT, DataSeeder.ADMIN_PASSWORD);
    String account = "cfm" + System.nanoTime();
    mvc.perform(post("/api/members").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("account", account, "name", "确认顾问", "password", "Advisor123", "role", "ADVISOR"))))
        .andExpect(status().isOk());
    MockHttpSession advisor = login(account, "Advisor123");
    String confirmedName = "CONFIRMED-" + account;
    String draftName = "DRAFTONLY-" + account;
    long confirmedSession = importWav(advisor, "已确认会话");
    mvc.perform(put("/api/sessions/" + confirmedSession + "/profile").session(advisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("appellation", confirmedName, "intent", "意向", "budget", "10万", "decisionMaker", "本人", "notes", "备注"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmationStatus").value("draft"));

    JsonNode before = objectMapper.readTree(mvc.perform(get("/api/confirmed/profiles").param("q", confirmedName).session(advisor))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString());
    assertFalse(containsAppellation(before, confirmedName));

    mvc.perform(post("/api/sessions/" + confirmedSession + "/profile/confirm").session(advisor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmationStatus").value("confirmed"));
    JsonNode after = objectMapper.readTree(mvc.perform(get("/api/confirmed/profiles").param("q", confirmedName).session(advisor))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString());
    assertTrue(containsAppellation(after, confirmedName));

    long draftSession = importWav(advisor, "草案会话");
    mvc.perform(put("/api/sessions/" + draftSession + "/profile").session(advisor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("appellation", draftName, "intent", "草案意向"))))
        .andExpect(status().isOk());
    JsonNode drafts = objectMapper.readTree(mvc.perform(get("/api/confirmed/profiles").param("q", draftName).session(advisor))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString());
    assertFalse(containsAppellation(drafts, draftName));

    MvcResult job = mvc.perform(post("/api/admin/exports").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "format", "JSON",
                "includeConfirmedProfiles", true,
                "includeConfirmedNeeds", true,
                "includeDrafts", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("succeeded"))
        .andReturn();
    long jobId = objectMapper.readTree(job.getResponse().getContentAsString()).get("id").asLong();
    String exported = mvc.perform(get("/api/admin/exports/" + jobId + "/download").session(admin))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertTrue(exported.contains(confirmedName));
    assertFalse(exported.contains(draftName));
    assertTrue(exported.contains("不包含草案"));

    mvc.perform(post("/api/sessions/" + confirmedSession + "/profile/unlock").session(advisor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmationStatus").value("draft"));
    JsonNode unlocked = objectMapper.readTree(mvc.perform(get("/api/confirmed/profiles").param("q", confirmedName).session(advisor))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString());
    assertFalse(containsAppellation(unlocked, confirmedName));
  }

  @Test
  void adminPolicyAndWebhookStayStubbed() throws Exception {
    MockHttpSession admin = login(DataSeeder.ADMIN_ACCOUNT, DataSeeder.ADMIN_PASSWORD);
    mvc.perform(put("/api/admin/consent-policy").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("validityDays", 45))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validityDays").value(45))
        .andExpect(jsonPath("$.blockWithoutConsent").value(true))
        .andExpect(jsonPath("$.requirePerSession").value(false));
    mvc.perform(post("/api/admin/webhooks").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("url", "http://example.com/hook", "secret", "secret-key-1", "events", java.util.List.of("profile.confirmed")))))
        .andExpect(status().isBadRequest());
    MvcResult created = mvc.perform(post("/api/admin/webhooks").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "url", "https://example.com/hook",
                "secret", "secret-key-1",
                "events", java.util.List.of("profile.confirmed", "need.confirmed", "transcript.succeeded")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.secretMasked").value("****-1"))
        .andReturn();
    long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    mvc.perform(post("/api/admin/webhooks/" + id + "/test").session(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statusCode").value(0))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("未向外部")));
    mvc.perform(patch("/api/members/1").session(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("enabled", false))))
        .andExpect(status().isConflict());
  }

  private long importWav(MockHttpSession session, String label) throws Exception {
    MockMultipartFile wav = new MockMultipartFile("file", label + ".wav", "audio/wav", new byte[]{1, 2, 3, 4});
    MvcResult result = mvc.perform(multipart("/api/sessions/import")
            .file(wav)
            .param("customerLabel", label)
            .param("consentAcknowledged", "true")
            .session(session))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  private boolean containsAppellation(JsonNode array, String name) {
    for (JsonNode node : array) {
      if (name.equals(node.path("appellation").asText())) {
        return true;
      }
    }
    return false;
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
