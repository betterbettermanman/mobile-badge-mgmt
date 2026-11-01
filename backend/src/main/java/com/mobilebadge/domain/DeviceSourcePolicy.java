package com.mobilebadge.domain;

import com.mobilebadge.common.ApiException;

/**
 * 一期采集源边界：MobileMic + FileImport 可用，工牌只占位。
 * 核心接口接收 {@link DeviceSource}，不把「仅手机」写进方法签名。
 */
public final class DeviceSourcePolicy {

  public static final String BADGE_NOTE = "真工牌采集将在二期开放，一期请使用手机录音或文件导入";

  private DeviceSourcePolicy() {
  }

  public static void assertCreatable(DeviceSource source) {
    if (source == null) {
      throw ApiException.badRequest("SOURCE_REQUIRED", "请选择采集来源");
    }
    if (source == DeviceSource.badge) {
      throw ApiException.badRequest("BADGE_NOT_AVAILABLE", "真工牌采集将在二期开放");
    }
  }

  public static void assertBindable(DeviceSource source) {
    if (source == DeviceSource.badge) {
      throw ApiException.badRequest("BADGE_NOT_AVAILABLE", "真工牌采集将在二期开放");
    }
    if (source == DeviceSource.file) {
      throw ApiException.badRequest("FILE_NOT_BINDABLE", "文件来源无需绑定，导入时自动标记");
    }
    if (source != DeviceSource.phone) {
      throw ApiException.badRequest("SOURCE_REQUIRED", "请选择采集来源");
    }
  }
}
