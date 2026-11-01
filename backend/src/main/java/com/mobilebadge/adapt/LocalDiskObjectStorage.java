package com.mobilebadge.adapt;

import com.mobilebadge.common.ApiException;
import com.mobilebadge.config.AppProperties;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LocalDiskObjectStorage implements ObjectStorage {

  private final Path root;

  @Autowired
  public LocalDiskObjectStorage(AppProperties properties) {
    this(Path.of(properties.getStorage().getRoot()));
  }

  public LocalDiskObjectStorage(Path root) {
    this.root = root.toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.root);
    } catch (IOException ex) {
      throw new IllegalStateException("无法创建对象存储目录", ex);
    }
  }

  @Override
  public String store(long sessionId, String filename, InputStream input) throws IOException {
    String cleaned = sanitize(filename);
    String key = sessionId + "/" + UUID.randomUUID() + "-" + cleaned;
    Path target = resolve(key);
    Files.createDirectories(target.getParent());
    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
    return key;
  }

  @Override
  public String storeBytes(String key, byte[] bytes) throws IOException {
    Path target = resolve(key);
    Files.createDirectories(target.getParent());
    Files.write(target, bytes);
    return key;
  }

  @Override
  public Path resolve(String storageKey) {
    if (storageKey == null || storageKey.isBlank()
        || storageKey.contains("..")
        || storageKey.startsWith("/")
        || storageKey.startsWith("\\")
        || storageKey.contains(":")) {
      throw ApiException.badRequest("STORAGE", "对象路径不合法");
    }
    Path target = root.resolve(storageKey).normalize();
    if (!target.startsWith(root)) {
      throw ApiException.badRequest("STORAGE", "对象路径不合法");
    }
    return target;
  }

  private static String sanitize(String filename) {
    String cleaned = filename == null ? "audio" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
    if (cleaned.isBlank() || ".".equals(cleaned) || "..".equals(cleaned)) {
      return "audio";
    }
    return cleaned;
  }
}
