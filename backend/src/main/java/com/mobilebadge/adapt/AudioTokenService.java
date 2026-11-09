package com.mobilebadge.adapt;

import com.mobilebadge.common.ApiException;
import com.mobilebadge.config.AppProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 本地对象的短时可读令牌。供云 ASR 拉取音频，不暴露存储键。 */
@Component
public class AudioTokenService {

  private final ObjectStorage storage;
  private final AppProperties properties;
  private final ConcurrentHashMap<String, Token> tokens = new ConcurrentHashMap<>();

  public AudioTokenService(ObjectStorage storage, AppProperties properties) {
    this.storage = storage;
    this.properties = properties;
  }

  public String presign(String storageKey, Duration ttl) {
    storage.resolve(storageKey);
    String token = UUID.randomUUID().toString().replace("-", "");
    tokens.put(token, new Token(storageKey, Instant.now().plus(ttl)));
    String base = properties.getPublicBaseUrl() == null ? "" : properties.getPublicBaseUrl().trim();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + "/api/internal/audio/" + token;
  }

  public Path resolve(String token) {
    Token stored = tokens.get(token);
    if (stored == null || !stored.expiresAt.isAfter(Instant.now())) {
      if (stored != null) {
        tokens.remove(token, stored);
      }
      throw ApiException.notFound();
    }
    return storage.resolve(stored.storageKey);
  }

  private record Token(String storageKey, Instant expiresAt) {
  }
}
