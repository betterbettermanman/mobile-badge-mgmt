package com.mobilebadge.adapt;

import java.util.Set;

public final class WebhookEvents {

  public static final String PROFILE_CONFIRMED = "profile.confirmed";
  public static final String NEED_CONFIRMED = "need.confirmed";
  public static final String TRANSCRIPT_SUCCEEDED = "transcript.succeeded";

  public static final Set<String> ALLOWED = Set.of(PROFILE_CONFIRMED, NEED_CONFIRMED, TRANSCRIPT_SUCCEEDED);

  private WebhookEvents() {
  }
}
