package com.deltafinance.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propriedades de sessão além da inatividade gerenciada pelo Spring Session. */
@ConfigurationProperties(prefix = "delta.security.sessions")
public class SessionProperties {

  /** Limite absoluto desde a criação da sessão autenticada (FR-003). */
  private Duration absoluteTimeout = Duration.ofHours(12);

  public Duration getAbsoluteTimeout() {
    return absoluteTimeout;
  }

  public void setAbsoluteTimeout(Duration absoluteTimeout) {
    this.absoluteTimeout = absoluteTimeout;
  }
}
