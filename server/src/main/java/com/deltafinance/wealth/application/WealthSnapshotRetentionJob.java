package com.deltafinance.wealth.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expurgo diário de snapshots patrimoniais fora da janela de retenção. */
@Component
@ConditionalOnProperty(
    prefix = "delta.wealth.snapshot",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WealthSnapshotRetentionJob {

  private static final Logger LOG = LoggerFactory.getLogger(WealthSnapshotRetentionJob.class);

  private final WealthSnapshotService snapshotService;

  public WealthSnapshotRetentionJob(WealthSnapshotService snapshotService) {
    this.snapshotService = snapshotService;
  }

  @Scheduled(
      cron = "${delta.wealth.snapshot.retention-cron:0 45 3 * * *}",
      zone = "America/Sao_Paulo")
  public void scheduled() {
    int deleted = snapshotService.purgeExpired();
    LOG.info("Retenção de snapshots patrimoniais: deleted={}", deleted);
  }
}
