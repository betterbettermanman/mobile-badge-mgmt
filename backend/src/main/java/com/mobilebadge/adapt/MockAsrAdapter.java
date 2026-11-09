package com.mobilebadge.adapt;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAsrAdapter implements AsrAdapter {

  private final AtomicInteger failuresRemaining = new AtomicInteger();

  public void failNext(int times) {
    failuresRemaining.set(Math.max(0, times));
  }

  @Override
  public AsrResult transcribe(long sessionId, String storageKey) {
    if (failuresRemaining.getAndUpdate(current -> current > 0 ? current - 1 : 0) > 0) {
      throw new VendorCallException("云分析暂时失败（厂商 5xx）", true);
    }
    List<AsrSentence> sentences = List.of(
        new AsrSentence("advisor", "您好，请问有什么需求？"),
        new AsrSentence("customer", "我想了解一下预算和决策流程。"),
        new AsrSentence("unknown", "（环境音，角色未判定）"));
    String full = "顾问：您好，请问有什么需求？\n客户：我想了解一下预算和决策流程。\n未知：（环境音，角色未判定）";
    return new AsrResult(full, sentences);
  }
}
