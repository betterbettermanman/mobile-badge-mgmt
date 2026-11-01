package com.mobilebadge.adapt;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MockAsrAdapter implements AsrAdapter {

  @Override
  public AsrResult transcribe(long sessionId, String storageKey) {
    List<AsrSentence> sentences = List.of(
        new AsrSentence("advisor", "您好，请问有什么需求？"),
        new AsrSentence("customer", "我想了解一下预算和决策流程。"),
        new AsrSentence("unknown", "（环境音，角色未判定）"));
    String full = "顾问：您好，请问有什么需求？\n客户：我想了解一下预算和决策流程。\n未知：（环境音，角色未判定）";
    return new AsrResult(full, sentences);
  }
}
