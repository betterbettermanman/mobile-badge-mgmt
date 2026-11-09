package com.mobilebadge.adapt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;

public final class SecretRedactor {

  private SecretRedactor() {
  }

  public static String redact(String text, Collection<String> secrets) {
    if (text == null) {
      return null;
    }
    String out = text;
    if (secrets != null) {
      for (String secret : secrets) {
        if (secret != null && secret.length() >= 4) {
          out = out.replace(secret, "[REDACTED]");
        }
      }
    }
    return out;
  }

  public static boolean constantEquals(String left, String right) {
    if (left == null || right == null) {
      return false;
    }
    return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }
}
