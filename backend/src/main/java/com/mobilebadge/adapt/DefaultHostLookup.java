package com.mobilebadge.adapt;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DefaultHostLookup implements HostLookup {

  @Override
  public boolean isPrivateOrLocal(String host) {
    if (host == null || host.isBlank()) {
      return true;
    }
    String normalized = host.toLowerCase(Locale.ROOT).trim();
    if ("localhost".equals(normalized) || normalized.endsWith(".localhost") || "0.0.0.0".equals(normalized)
        || "::1".equals(normalized) || "[::1]".equals(normalized)) {
      return true;
    }
    try {
      for (InetAddress address : InetAddress.getAllByName(normalized)) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()) {
          return true;
        }
      }
      return false;
    } catch (UnknownHostException ex) {
      return true;
    }
  }
}
