package com.mobilebadge.adapt;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

public interface WebhookPoster {

  int post(URI uri, String body, Map<String, String> headers, int connectTimeoutMs, int readTimeoutMs) throws IOException;
}
