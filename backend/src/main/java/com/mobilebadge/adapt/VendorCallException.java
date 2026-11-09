package com.mobilebadge.adapt;

/** 厂商调用失败。message 必须是可展示的友好摘要，不得包含密钥。 */
public class VendorCallException extends RuntimeException {

  private final boolean retryable;

  public VendorCallException(String message, boolean retryable) {
    super(message);
    this.retryable = retryable;
  }

  public boolean retryable() {
    return retryable;
  }
}
