package com.deltafinance.connections.api;

import java.time.Instant;
import java.util.UUID;

/** Execução de sincronização exposta ao painel de status. */
public record SyncRunResponse(
    UUID id,
    UUID integrationId,
    UUID connectionId,
    String status,
    Instant startedAt,
    Instant finishedAt,
    int readCount,
    int createdCount,
    int updatedCount,
    int skippedCount,
    int failedCount,
    Long durationMs,
    String errorCode) {}
