package com.mobilebadge.adapt;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JdkWebhookPoster implements WebhookPoster {

  @Override
  public int post(URI uri, String body, Map<String, String> headers, int connectTimeoutMs, int readTimeoutMs) throws IOException {
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(Math.max(connectTimeoutMs, 1)))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMillis(Math.max(readTimeoutMs, 1)))
        .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
    if (headers != null) {
      headers.forEach(builder::header);
    }
    try {
      return client.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("webhook interrupted", ex);
    }
  }
}
