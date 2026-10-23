package com.deltafinance.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Limites de proteção de login (FR-008). */
@ConfigurationProperties(prefix = "delta.security.login")
public class LoginProtectionProperties {

  /** Falhas permitidas dentro da janela. */
  private int maxFailures = 5;

  /** Janela móvel de contagem. */
  private Duration window = Duration.ofMinutes(15);

  /** Duração do bloqueio após exceder o limite. */
  private Duration blockDuration = Duration.ofMinutes(15);

  public int getMaxFailures() {
    return maxFailures;
  }

  public void setMaxFailures(int maxFailures) {
    this.maxFailures = maxFailures;
  }

  public Duration getWindow() {
    return window;
  }

  public void setWindow(Duration window) {
    this.window = window;
  }

  public Duration getBlockDuration() {
    return blockDuration;
  }

  public void setBlockDuration(Duration blockDuration) {
    this.blockDuration = blockDuration;
  }
}
