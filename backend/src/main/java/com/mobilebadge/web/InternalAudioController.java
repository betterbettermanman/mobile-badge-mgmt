package com.mobilebadge.web;

import com.mobilebadge.adapt.AudioTokenService;
import com.mobilebadge.common.ApiException;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalAudioController {

  private final AudioTokenService tokens;

  public InternalAudioController(AudioTokenService tokens) {
    this.tokens = tokens;
  }

  @GetMapping("/api/internal/audio/{token}")
  public ResponseEntity<Resource> audio(@PathVariable String token) {
    Path path = tokens.resolve(token);
    if (!path.toFile().isFile()) {
      throw ApiException.notFound();
    }
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    MediaType media = MediaType.APPLICATION_OCTET_STREAM;
    if (name.endsWith(".mp3")) {
      media = MediaType.parseMediaType("audio/mpeg");
    } else if (name.endsWith(".m4a")) {
      media = MediaType.parseMediaType("audio/mp4");
    } else if (name.endsWith(".aac")) {
      media = MediaType.parseMediaType("audio/aac");
    } else if (name.endsWith(".wav")) {
      media = MediaType.parseMediaType("audio/wav");
    }
    return ResponseEntity.ok().contentType(media).body(new FileSystemResource(path));
  }
}
