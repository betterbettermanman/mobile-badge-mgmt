package com.mobilebadge.adapt;

import java.util.List;

/** 云 ASR 适配器。一期仅提供 Mock，不接自研或真实云厂商。 */
public interface AsrAdapter {

  AsrResult transcribe(long sessionId, String storageKey);

  record AsrSentence(String speaker, String text) {
  }

  record AsrResult(String fullText, List<AsrSentence> sentences) {
  }
}
