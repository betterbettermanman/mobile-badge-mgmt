package com.mobilebadge.adapt;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mobilebadge.config.AppProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CloudAdapterContractTest {

  private WireMockServer server;

  @BeforeEach
  void start() {
    server = new WireMockServer(wireMockConfig().dynamicPort());
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop();
  }

  @Test
  void volcSubmitEnablesDiarizationHotwordsAndQueryMapsSpeakers() {
    server.stubFor(post(urlEqualTo("/submit")).willReturn(aResponse()
        .withStatus(200)
        .withHeader("X-Api-Status-Code", "20000000")
        .withBody("{}")));
    server.stubFor(post(urlEqualTo("/query")).willReturn(aResponse()
        .withStatus(200)
        .withHeader("X-Api-Status-Code", "20000000")
        .withBody("""
            {"result":{"text":"你好 我想看预算","utterances":[
              {"text":"你好","additions":{"speaker":"1"}},
              {"text":"我想看预算","additions":{"speaker":"2"}}
            ]}}
            """)));
    VolcAsrAdapter adapter = new VolcAsrAdapter(volc("app-key", "access-key"), List.of("app-key", "access-key"), HttpClient.newHttpClient());
    String taskId = adapter.submit(9L, "9/talk.wav", "https://audio.example/talk.wav", List.of("翡翠手镯"));
    server.verify(postRequestedFor(urlEqualTo("/submit"))
        .withHeader("X-Api-App-Key", equalTo("app-key"))
        .withHeader("X-Api-Access-Key", equalTo("access-key"))
        .withHeader("X-Api-Resource-Id", equalTo("volc.bigasr.auc"))
        .withHeader("X-Api-Request-Id", equalTo(taskId))
        .withRequestBody(containing("\"enable_speaker_info\":true"))
        .withRequestBody(containing("\"show_utterances\":true"))
        .withRequestBody(containing("翡翠手镯")));
    AsrAdapter.AsrPoll poll = adapter.fetch(taskId);
    assertEquals(AsrAdapter.AsrPollState.SUCCEEDED, poll.state());
    assertEquals("spk0", poll.result().sentences().get(0).speaker());
    assertEquals("spk1", poll.result().sentences().get(1).speaker());
  }

  @Test
  void volcServerErrorIsFriendlyAndMissingKeyDoesNotEchoSecret() {
    server.stubFor(post(urlEqualTo("/query")).willReturn(aResponse()
        .withStatus(200)
        .withHeader("X-Api-Status-Code", "55000031")
        .withBody("access-key-should-not-leak")));
    VolcAsrAdapter adapter = new VolcAsrAdapter(volc("app-key", "access-key-should-not-leak"), List.of("access-key-should-not-leak"), HttpClient.newHttpClient());
    VendorCallException failed = assertThrows(VendorCallException.class, () -> adapter.fetch("task-1"));
    assertTrue(failed.retryable());
    assertTrue(failed.getMessage().contains("5xx"));
    assertFalse(failed.getMessage().contains("access-key-should-not-leak"));

    VolcAsrAdapter missing = new VolcAsrAdapter(volc("", ""), List.of(), HttpClient.newHttpClient());
    VendorCallException notConfigured = assertThrows(VendorCallException.class,
        () -> missing.submit(1L, "1/a.wav", "https://audio.example/a.wav", List.of()));
    assertTrue(notConfigured.getMessage().contains("环境变量"));
    assertFalse(notConfigured.getMessage().contains("sk-"));
  }

  @Test
  void bailianRequestsJsonDraftAndDropsConfirmedFlag() throws Exception {
    String content = """
        {"profile":{"appellation":"李先生","intent":"咨询","budget":"","decisionMaker":"本人","notes":"草案","confirmationStatus":"confirmed"},"needs":[{"title":"看方案","detail":"先比较"}]}
        """;
    String body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of(
        "choices", java.util.List.of(java.util.Map.of("message", java.util.Map.of("content", content)))));
    server.stubFor(post(urlEqualTo("/chat/completions")).willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(body)));
    BailianLlmAdapter adapter = new BailianLlmAdapter(bailian("dashscope-key-should-not-leak"), List.of("dashscope-key-should-not-leak"), HttpClient.newHttpClient());
    LlmAdapter.Extraction extraction = adapter.extract("李先生", "客户想看方案");
    assertEquals("李先生", extraction.profile().appellation());
    assertEquals("看方案", extraction.needs().get(0).title());
    server.verify(postRequestedFor(urlEqualTo("/chat/completions"))
        .withHeader("Authorization", equalTo("Bearer dashscope-key-should-not-leak"))
        .withRequestBody(containing("\"model\":\"qwen3.7-flash\""))
        .withRequestBody(containing("\"response_format\":{\"type\":\"json_object\"}")));

    BailianLlmAdapter missing = new BailianLlmAdapter(bailian(""), List.of("dashscope-key-should-not-leak"), HttpClient.newHttpClient());
    VendorCallException error = assertThrows(VendorCallException.class, () -> missing.extract("甲", "文本"));
    assertFalse(error.getMessage().contains("dashscope-key-should-not-leak"));
  }

  @Test
  void bailianHttp500IsRetryableWithoutKey() {
    server.stubFor(post(urlEqualTo("/chat/completions")).willReturn(aResponse().withStatus(503).withBody("dashscope-key-should-not-leak")));
    BailianLlmAdapter adapter = new BailianLlmAdapter(bailian("dashscope-key-should-not-leak"), List.of("dashscope-key-should-not-leak"), HttpClient.newHttpClient());
    VendorCallException error = assertThrows(VendorCallException.class, () -> adapter.extract("甲", "文本"));
    assertTrue(error.retryable());
    assertFalse(error.getMessage().contains("dashscope-key-should-not-leak"));
  }

  private AppProperties.Volc volc(String appKey, String accessKey) {
    AppProperties.Volc volc = new AppProperties.Volc();
    volc.setAppKey(appKey);
    volc.setAccessKey(accessKey);
    volc.setResourceId("volc.bigasr.auc");
    volc.setSubmitUrl(server.baseUrl() + "/submit");
    volc.setQueryUrl(server.baseUrl() + "/query");
    volc.setUid("mobile-badge");
    return volc;
  }

  private AppProperties.Bailian bailian(String apiKey) {
    AppProperties.Bailian bailian = new AppProperties.Bailian();
    bailian.setApiKey(apiKey);
    bailian.setModel("qwen3.7-flash");
    bailian.setBaseUrl(server.baseUrl());
    return bailian;
  }

  @Test
  void audioTokenIsShortLivedAndDoesNotAcceptRawKeys() throws Exception {
    java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("badge-audio");
    LocalDiskObjectStorage storage = new LocalDiskObjectStorage(dir);
    String key = storage.store(3L, "a.wav", new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}));
    AppProperties properties = new AppProperties();
    properties.setPublicBaseUrl("http://localhost:8092");
    AudioTokenService tokens = new AudioTokenService(storage, properties);
    String url = tokens.presign(key, Duration.ofMinutes(5));
    assertTrue(url.startsWith("http://localhost:8092/api/internal/audio/"));
    assertFalse(url.contains(key));
    String token = url.substring(url.lastIndexOf('/') + 1);
    assertTrue(java.nio.file.Files.exists(tokens.resolve(token)));
    org.junit.jupiter.api.Assertions.assertThrows(com.mobilebadge.common.ApiException.class, () -> tokens.resolve("../" + key));
  }
}
