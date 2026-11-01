package com.mobilebadge.adapt;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobilebadge.common.ApiException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalDiskObjectStorageTest {

  @Test
  void storesUnderSessionIdAndRejectsTraversal() throws Exception {
    Path dir = Files.createTempDirectory("badge-storage");
    LocalDiskObjectStorage storage = new LocalDiskObjectStorage(dir);
    String key = storage.store(9L, "a.wav", new ByteArrayInputStream(new byte[]{1, 2, 3}));
    assertTrue(key.startsWith("9/"));
    assertTrue(Files.exists(storage.resolve(key)));
    assertThrows(ApiException.class, () -> storage.resolve("../secret"));
    assertThrows(ApiException.class, () -> storage.resolve("/etc/passwd"));
    assertThrows(ApiException.class, () -> storage.storeBytes("exports/../../secret.txt", new byte[]{1}));
  }
}
