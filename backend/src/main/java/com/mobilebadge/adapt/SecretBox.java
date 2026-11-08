package com.mobilebadge.adapt;

import com.mobilebadge.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Webhook HMAC secret 的可选静态加密。未配置 WEBHOOK_DATA_KEY 时保持原样，便于本地与测试。 */
@Component
public class SecretBox {

  private final byte[] key;

  @Autowired
  public SecretBox(AppProperties properties) {
    this(properties.getWebhook().getDataKey());
  }

  SecretBox(String dataKey) {
    if (dataKey == null || dataKey.isBlank()) {
      this.key = null;
      return;
    }
    try {
      this.key = MessageDigest.getInstance("SHA-256").digest(dataKey.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new IllegalStateException("无法初始化密钥加密", ex);
    }
  }

  public String seal(String plain) {
    if (plain == null || key == null) {
      return plain;
    }
    try {
      byte[] iv = new byte[12];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
      byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] packed = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, packed, 0, iv.length);
      System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
      return "enc:v1:" + Base64.getEncoder().encodeToString(packed);
    } catch (Exception ex) {
      throw new IllegalStateException("密钥加密失败", ex);
    }
  }

  public String open(String stored) {
    if (stored == null || !stored.startsWith("enc:v1:")) {
      return stored;
    }
    if (key == null) {
      throw new IllegalStateException("缺少 WEBHOOK_DATA_KEY，无法解密");
    }
    try {
      byte[] packed = Base64.getDecoder().decode(stored.substring("enc:v1:".length()));
      byte[] iv = new byte[12];
      System.arraycopy(packed, 0, iv, 0, iv.length);
      byte[] encrypted = new byte[packed.length - iv.length];
      System.arraycopy(packed, iv.length, encrypted, 0, encrypted.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new IllegalStateException("密钥解密失败", ex);
    }
  }
}
