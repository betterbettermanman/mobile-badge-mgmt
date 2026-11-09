package com.mobilebadge.adapt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobilebadge.adapt.LlmAdapter.Extraction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class Phase2RulesTest {

  @Test
  void draftJsonIgnoresConfirmationAndRequiresSchema() {
    Extraction extraction = DraftJson.parse("""
        {"confirmationStatus":"confirmed","profile":{"appellation":"王女士","intent":"看看","budget":"3万","decisionMaker":"本人","notes":"草案","confirmationStatus":"confirmed"},"needs":[{"title":"比较预算","detail":"想先看方案"}]}
        """);
    assertEquals("王女士", extraction.profile().appellation());
    assertEquals("比较预算", extraction.needs().get(0).title());
    assertEquals(1, extraction.needs().size());
    assertThrows(VendorCallException.class, () -> DraftJson.parse("not-json"));
    assertThrows(VendorCallException.class, () -> DraftJson.parse("{\"profile\":[]}"));
  }

  @Test
  void secretsAreRedactedAndComparedInConstantTime() {
    String redacted = SecretRedactor.redact("token=sk-live-should-not-appear failed", java.util.List.of("sk-live-should-not-appear"));
    assertFalse(redacted.contains("sk-live-should-not-appear"));
    assertTrue(redacted.contains("[REDACTED]"));
    assertTrue(SecretRedactor.constantEquals("same-secret", "same-secret"));
    assertFalse(SecretRedactor.constantEquals("same-secret", "other-secret"));
  }

  @Test
  void secretBoxDoesNotStorePlaintextWhenKeyPresent() {
    SecretBox box = new SecretBox("unit-test-data-key");
    String sealed = box.seal("super-secret-hook");
    assertFalse(sealed.contains("super-secret-hook"));
    assertTrue(sealed.startsWith("enc:v1:"));
    assertEquals("super-secret-hook", box.open(sealed));
    assertEquals("plain-dev", new SecretBox("").seal("plain-dev"));
  }

  @Test
  void webhookRulesRejectEmptyAllowlistHttpAndSignBody() throws Exception {
    assertEquals(WebhookPolicyRules.Block.EMPTY_ALLOWLIST,
        WebhookPolicyRules.evaluate("https://hooks.example/a", "  ", true, false));
    assertEquals(WebhookPolicyRules.Block.NOT_HTTPS,
        WebhookPolicyRules.evaluate("http://hooks.example/a", "hooks.example", true, false));
    assertEquals(WebhookPolicyRules.Block.NOT_ALLOWLISTED,
        WebhookPolicyRules.evaluate("https://evil.example/a", "hooks.example", true, false));
    assertEquals(WebhookPolicyRules.Block.PRIVATE_TARGET,
        WebhookPolicyRules.evaluate("https://hooks.example/a", "hooks.example", true, true));
    assertEquals(WebhookPolicyRules.Block.NONE,
        WebhookPolicyRules.evaluate("https://hooks.example/a", "hooks.example", true, false));
    assertTrue(WebhookPolicyRules.skipDetail(WebhookPolicyRules.Block.EMPTY_ALLOWLIST).contains("未向外部"));

    String body = "{\"event\":\"profile.confirmed\"}";
    String header = WebhookPolicyRules.signatureHeader("secret-key-1", "1700000000", body);
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("secret-key-1".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] raw = mac.doFinal(("1700000000." + body).getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder();
    for (byte value : raw) {
      hex.append(String.format("%02x", value));
    }
    assertEquals("t=1700000000,v1=" + hex, header);
    assertEquals(Instant.parse("2026-11-08T00:01:00Z"),
        WebhookPolicyRules.nextRetry(1, new int[] {60, 300, 1800}, Instant.parse("2026-11-08T00:00:00Z"), 4));
    assertEquals(null, WebhookPolicyRules.nextRetry(4, new int[] {60, 300, 1800}, Instant.parse("2026-11-08T00:00:00Z"), 4));
  }

  @Test
  void speakerIdsMapToSpkLabels() {
    assertEquals("spk0", VolcAsrAdapter.mapSpeaker("1"));
    assertEquals("spk1", VolcAsrAdapter.mapSpeaker("2"));
    assertEquals("spk3", VolcAsrAdapter.mapSpeaker("spk3"));
  }
}
