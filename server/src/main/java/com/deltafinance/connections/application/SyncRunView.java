package com.deltafinance.connections.application;

import java.time.Instant;
import java.util.UUID;

/** Visão pública de uma execução de sincronização. */
public record SyncRunView(
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
