package com.mobilebadge.adapt;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookPolicyRules {

  public enum Block {
    NONE,
    NOT_HTTPS,
    EMPTY_ALLOWLIST,
    DISABLED,
    NOT_ALLOWLISTED,
    PRIVATE_TARGET
  }

  private WebhookPolicyRules() {
  }

  public static Block evaluate(String url, String allowlist, boolean outboundEnabled, boolean privateHost) {
    if (!isHttps(url)) {
      return Block.NOT_HTTPS;
    }
    if (allowlist == null || allowlist.isBlank()) {
      return Block.EMPTY_ALLOWLIST;
    }
    if (!outboundEnabled) {
      return Block.DISABLED;
    }
    if (!hostAllowed(url, allowlist)) {
      return Block.NOT_ALLOWLISTED;
    }
    if (privateHost) {
      return Block.PRIVATE_TARGET;
    }
    return Block.NONE;
  }

  public static boolean isHttps(String url) {
    try {
      URI uri = URI.create(url == null ? "" : url.trim());
      return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank();
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  public static boolean hostAllowed(String url, String allowlist) {
    URI uri;
    try {
      uri = URI.create(url.trim());
    } catch (IllegalArgumentException ex) {
      return false;
    }
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    for (String raw : allowlist.split("[,，]")) {
      String entry = raw.trim().toLowerCase(Locale.ROOT);
      if (entry.isEmpty()) {
        continue;
      }
      if (entry.startsWith("https://") || entry.startsWith("http://")) {
        if (url.trim().toLowerCase(Locale.ROOT).startsWith(entry)) {
          return true;
        }
        try {
          String entryHost = URI.create(entry).getHost();
          if (entryHost != null && host.equals(entryHost.toLowerCase(Locale.ROOT))) {
            return true;
          }
        } catch (IllegalArgumentException ignored) {
          continue;
        }
      } else if (host.equals(entry) || host.endsWith("." + entry)) {
        return true;
      }
    }
    return false;
  }

  public static String skipDetail(Block block) {
    return switch (block) {
      case NOT_HTTPS -> "拒绝 http://，未向外部 URL 投递";
      case EMPTY_ALLOWLIST -> "白名单为空，未向外部 URL 投递";
      case DISABLED -> "出站未启用，未向外部 URL 投递";
      case NOT_ALLOWLISTED -> "不在白名单，未向外部 URL 投递";
      case PRIVATE_TARGET -> "目标地址为私网，未向外部 URL 投递";
      case NONE -> "";
    };
  }

  public static String signatureHeader(String secret, String timestamp, String body) {
    return "t=" + timestamp + ",v1=" + sign(secret, timestamp, body);
  }

  public static String sign(String secret, String timestamp, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw = mac.doFinal((timestamp + "." + (body == null ? "" : body)).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(raw.length * 2);
      for (byte value : raw) {
        hex.append(String.format("%02x", value));
      }
      return hex.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("无法计算 Webhook 签名", ex);
    }
  }

  public static Instant nextRetry(int attempt, int[] delays, Instant now, int maxAttempts) {
    if (attempt >= maxAttempts || delays == null || delays.length == 0) {
      return null;
    }
    int index = Math.min(Math.max(attempt - 1, 0), delays.length - 1);
    return now.plusSeconds(delays[index]);
  }
}
