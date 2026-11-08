package com.mobilebadge.service;

import org.springframework.stereotype.Component;

/** 测试可在 LLM 阶段暂停，生产默认不暂停。 */
@Component
public class AnalysisControl {

  private volatile boolean pauseBeforeLlm;

  public void pauseBeforeLlm(boolean pause) {
    this.pauseBeforeLlm = pause;
  }

  public boolean pauseBeforeLlm() {
    return pauseBeforeLlm;
  }
}
