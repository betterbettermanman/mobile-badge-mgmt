package com.mobilebadge.adapt;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.llm", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmAdapter implements LlmAdapter {

  @Override
  public ProfileDraft extractProfile(String customerLabel, String transcript) {
    String name = customerLabel == null || customerLabel.isBlank() ? "未命名客户" : customerLabel.trim();
    return new ProfileDraft(
        name,
        "到店咨询（草案）",
        "未提及",
        "待确认",
        "由 Mock LLM 根据转写生成，请人工核对。确认前不会进入已确认视图。");
  }

  @Override
  public List<NeedDraft> extractNeeds(String transcript) {
    return List.of(new NeedDraft("了解产品与预算", "模型草案：客户提到预算和决策流程，需顾问补全后确认。"));
  }
}
