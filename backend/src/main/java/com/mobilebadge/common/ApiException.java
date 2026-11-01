package com.mobilebadge.common;

public class ApiException extends RuntimeException {

  private final int status;
  private final String code;

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static ApiException badRequest(String code, String message) {
    return new ApiException(400, code, message);
  }

  public static ApiException badCredentials() {
    return new ApiException(401, "BAD_CREDENTIALS", "账号或密码不正确");
  }

  public static ApiException unauthenticated() {
    return new ApiException(401, "UNAUTHORIZED", "未登录");
  }

  public static ApiException forbidden() {
    return new ApiException(403, "FORBIDDEN", "无权访问该页面");
  }

  public static ApiException forbidden(String code, String message) {
    return new ApiException(403, code, message);
  }

  public static ApiException notFound() {
    return new ApiException(404, "NOT_FOUND", "内容不存在");
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(409, code, message);
  }
}
