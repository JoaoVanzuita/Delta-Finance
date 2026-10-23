package com.deltafinance.integrations.infrastructure;

import java.time.Duration;

/** Limite de requisições do provedor (429). */
public class PluggyRateLimitedException extends PluggyException {

  private final Duration retryAfter;

  public PluggyRateLimitedException(Duration retryAfter) {
    super("Limite de requisições do provedor atingido.");
    this.retryAfter = retryAfter == null ? Duration.ofSeconds(60) : retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
