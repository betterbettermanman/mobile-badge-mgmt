package com.mobilebadge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 采集来源抽象。一期可提交 {@code phone} 与 {@code file}；
 * {@code badge} 对应未来的 badgeDeviceId，只保留类型，不允许绑定或开录。
 */
public enum DeviceSource {
  phone,
  file,
  badge;

  @JsonCreator
  public static DeviceSource parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return switch (raw.trim()) {
      case "phone" -> phone;
      case "file" -> file;
      case "badge", "badgeDeviceId" -> badge;
      default -> throw new IllegalArgumentException("未知采集来源");
    };
  }
}
