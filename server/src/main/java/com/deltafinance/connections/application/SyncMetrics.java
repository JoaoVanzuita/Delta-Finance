package com.deltafinance.connections.application;

import com.deltafinance.connections.domain.SyncJobStatus;
import com.deltafinance.connections.domain.SyncRun;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Métricas de sincronização.
 *
 * <p>Nunca recebe valores financeiros, descrições ou identificadores externos; apenas estados,
 * contadores e códigos sanitizados.
 */
@Component
public class SyncMetrics {

  private final MeterRegistry registry;

  public SyncMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordRun(SyncRun run) {
    Counter.builder("delta.sync.runs")
        .description("Tentativas de sincronização por resultado")
        .tag("status", run.getStatus().name())
        .register(registry)
        .increment();
    if (run.getDurationMs() != null) {
      Timer.builder("delta.sync.duration")
          .description("Duração de sincronizações")
          .tag("status", run.getStatus().name())
          .register(registry)
          .record(Duration.ofMillis(run.getDurationMs()));
    }
    if (run.getErrorCode() != null) {
      recordFailure(run.getErrorCode());
    }
    recordRecords(run.getCreatedCount(), run.getUpdatedCount(), run.getSkippedCount());
  }

  public void recordFailure(String errorCode) {
    Counter.builder("delta.sync.failures")
        .description("Falhas de sincronização por código sanitizado")
        .tag("code", errorCode)
        .register(registry)
        .increment();
  }

  public void recordJob(SyncJobStatus status) {
    Counter.builder("delta.sync.jobs")
        .description("Jobs processados por estado final")
        .tag("status", status.name())
        .register(registry)
        .increment();
  }

  private void recordRecords(int created, int updated, int skipped) {
    if (created > 0) {
      Counter.builder("delta.sync.records")
          .tag("outcome", "created")
          .register(registry)
          .increment(created);
    }
    if (updated > 0) {
      Counter.builder("delta.sync.records")
          .tag("outcome", "updated")
          .register(registry)
          .increment(updated);
    }
    if (skipped > 0) {
      Counter.builder("delta.sync.records")
          .tag("outcome", "skipped")
          .register(registry)
          .increment(skipped);
    }
  }
}
