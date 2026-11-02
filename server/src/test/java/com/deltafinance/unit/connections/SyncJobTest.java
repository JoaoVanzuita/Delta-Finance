package com.deltafinance.unit.connections;

import static org.assertj.core.api.Assertions.assertThat;

import com.deltafinance.connections.domain.SyncJob;
import com.deltafinance.connections.domain.SyncJobStatus;
import com.deltafinance.connections.domain.SyncJobType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Devolver um job por contenção de lock não pode consumir tentativa. */
class SyncJobTest {

  private static SyncJob job() {
    return new SyncJob(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        SyncJobType.SYNC_CONNECTION,
        Instant.parse("2026-11-02T10:00:00Z"),
        5);
  }

  @Test
  void contencaoDeLockDevolveJobSemConsumirTentativa() {
    SyncJob job = job();
    job.markRunning("worker", Instant.parse("2026-11-02T10:00:01Z"));
    assertThat(job.getAttempts()).isEqualTo(1);

    job.releaseAfterLockContention(Instant.parse("2026-11-02T10:00:30Z"));

    assertThat(job.getAttempts()).isZero();
    assertThat(job.getStatus()).isEqualTo(SyncJobStatus.RETRY);
    assertThat(job.getLockedBy()).isNull();
    assertThat(job.getRunAfter()).isEqualTo(Instant.parse("2026-11-02T10:00:30Z"));
  }

  @Test
  void contencaoNaoProduzTentativaNegativa() {
    SyncJob job = job();

    job.releaseAfterLockContention(Instant.parse("2026-11-02T10:00:30Z"));

    assertThat(job.getAttempts()).isZero();
  }
}
