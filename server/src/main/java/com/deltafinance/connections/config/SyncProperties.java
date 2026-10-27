package com.deltafinance.connections.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Janelas e cadência da sincronização.
 *
 * <p>Os valores são configuráveis e documentados em {@code docs/operations.md}. O padrão segue o
 * FR-033: carga inicial de 12 meses, reprocessamento diário de 7 dias com sobreposição e
 * reconciliação mensal de 12 meses.
 */
@ConfigurationProperties(prefix = "delta.sync")
public class SyncProperties {

  private int initialWindowMonths = 12;

  private int dailyWindowDays = 7;

  private int reconciliationWindowMonths = 12;

  private int reconciliationDayOfMonth = 1;

  private boolean schedulerEnabled = true;

  private Duration schedulerInterval = Duration.ofMinutes(1);

  private Duration initialDelay = Duration.ofSeconds(30);

  private Duration jitter = Duration.ofSeconds(60);

  private int maxAttempts = 5;

  private Duration retryBase = Duration.ofSeconds(30);

  private Duration retryMax = Duration.ofHours(1);

  private int batchSize = 10;

  /** Tempo máximo de uma tentativa em memória (ADR-0004). */
  private Duration jobTimeout = Duration.ofMinutes(10);

  public int getInitialWindowMonths() {
    return initialWindowMonths;
  }

  public void setInitialWindowMonths(int initialWindowMonths) {
    this.initialWindowMonths = Math.max(1, initialWindowMonths);
  }

  public int getDailyWindowDays() {
    return dailyWindowDays;
  }

  public void setDailyWindowDays(int dailyWindowDays) {
    this.dailyWindowDays = Math.max(1, dailyWindowDays);
  }

  public int getReconciliationWindowMonths() {
    return reconciliationWindowMonths;
  }

  public void setReconciliationWindowMonths(int reconciliationWindowMonths) {
    this.reconciliationWindowMonths = Math.max(1, reconciliationWindowMonths);
  }

  public int getReconciliationDayOfMonth() {
    return reconciliationDayOfMonth;
  }

  public void setReconciliationDayOfMonth(int reconciliationDayOfMonth) {
    this.reconciliationDayOfMonth = Math.min(28, Math.max(1, reconciliationDayOfMonth));
  }

  public boolean isSchedulerEnabled() {
    return schedulerEnabled;
  }

  public void setSchedulerEnabled(boolean schedulerEnabled) {
    this.schedulerEnabled = schedulerEnabled;
  }

  public Duration getSchedulerInterval() {
    return schedulerInterval;
  }

  public void setSchedulerInterval(Duration schedulerInterval) {
    this.schedulerInterval = schedulerInterval;
  }

  public Duration getInitialDelay() {
    return initialDelay;
  }

  public void setInitialDelay(Duration initialDelay) {
    this.initialDelay = initialDelay;
  }

  public Duration getJitter() {
    return jitter;
  }

  public void setJitter(Duration jitter) {
    this.jitter = jitter;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = Math.max(1, maxAttempts);
  }

  public Duration getRetryBase() {
    return retryBase;
  }

  public void setRetryBase(Duration retryBase) {
    this.retryBase = retryBase;
  }

  public Duration getRetryMax() {
    return retryMax;
  }

  public void setRetryMax(Duration retryMax) {
    this.retryMax = retryMax;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = Math.max(1, batchSize);
  }

  public Duration getJobTimeout() {
    return jobTimeout;
  }

  public void setJobTimeout(Duration jobTimeout) {
    this.jobTimeout =
        jobTimeout == null || jobTimeout.isNegative() || jobTimeout.isZero()
            ? Duration.ofMinutes(10)
            : jobTimeout;
  }

  /** Backoff exponencial limitado para retries temporários. */
  public Duration retryDelay(int attempts) {
    long multiplier = 1L << Math.min(10, Math.max(0, attempts - 1));
    Duration delay = retryBase.multipliedBy(multiplier);
    return delay.compareTo(retryMax) > 0 ? retryMax : delay;
  }
}
