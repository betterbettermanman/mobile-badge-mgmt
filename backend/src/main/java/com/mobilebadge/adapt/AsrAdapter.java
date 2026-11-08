package com.mobilebadge.adapt;

import java.util.List;

/** 云 ASR 适配器。Mock 走同步 transcribe；火山豆包走 submit + fetch。 */
public interface AsrAdapter {

  AsrResult transcribe(long sessionId, String storageKey);

  default boolean async() {
    return false;
  }

  default String submit(long sessionId, String storageKey, String audioUrl, List<String> hotwords) {
    throw new UnsupportedOperationException("submit");
  }

  default AsrPoll fetch(String vendorTaskId) {
    throw new UnsupportedOperationException("fetch");
  }

  record AsrSentence(String speaker, String text) {
  }

  record AsrResult(String fullText, List<AsrSentence> sentences) {
  }

  enum AsrPollState {
    PENDING,
    SUCCEEDED,
    FAILED
  }

  record AsrPoll(AsrPollState state, AsrResult result, String error) {
  }
}
