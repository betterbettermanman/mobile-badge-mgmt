package com.mobilebadge.common;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> api(ApiException ex) {
    return ResponseEntity.status(ex.status()).body(new ApiError(ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> denied(AccessDeniedException ex) {
    return ResponseEntity.status(403).body(new ApiError("FORBIDDEN", "无权访问该页面"));
  }

  @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
  public ResponseEntity<ApiError> missingAuth(AuthenticationCredentialsNotFoundException ex) {
    return ResponseEntity.status(401).body(new ApiError("UNAUTHORIZED", "未登录"));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(error -> error.getDefaultMessage() == null ? "参数不正确" : error.getDefaultMessage())
        .orElse("参数不正确");
    return ResponseEntity.badRequest().body(new ApiError("VALIDATION", message));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest().body(new ApiError("VALIDATION", "请求格式不正确"));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException ex) {
    return ResponseEntity.badRequest().body(new ApiError("FILE_TOO_LARGE", "文件超过大小限制（默认 200MB）"));
  }
}
