package com.mobilebadge.adapt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** 本地或未来对象存储。音频键必须以 sessionId 为第一段。 */
public interface ObjectStorage {

  String store(long sessionId, String filename, InputStream input) throws IOException;

  String storeBytes(String key, byte[] bytes) throws IOException;

  Path resolve(String storageKey);
}
