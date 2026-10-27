package com.deltafinance.integrations.infrastructure;

import java.time.Duration;
import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração do provedor Pluggy (Meu Pluggy). */
@ConfigurationProperties(prefix = "delta.pluggy")
public class PluggyProperties {

  private String baseUrl = "https://api.pluggy.ai";

  /** Limite de conexões da modalidade pessoal, versionado e datado (FR-022). */
  private int connectionLimit = 5;

  private LocalDate connectionLimitCheckedAt = LocalDate.of(2026, 10, 22);

  private Duration requestTimeout = Duration.ofSeconds(30);

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public int getConnectionLimit() {
    return connectionLimit;
  }

  public void setConnectionLimit(int connectionLimit) {
    this.connectionLimit = connectionLimit;
  }

  public LocalDate getConnectionLimitCheckedAt() {
    return connectionLimitCheckedAt;
  }

  public void setConnectionLimitCheckedAt(LocalDate connectionLimitCheckedAt) {
    this.connectionLimitCheckedAt = connectionLimitCheckedAt;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }
}
