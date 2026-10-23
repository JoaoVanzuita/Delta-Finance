package com.deltafinance.transactions.application;

/** Contadores de uma janela de importação. */
public record ImportStats(int read, int created, int updated, int skipped, int failed) {

  public static ImportStats empty() {
    return new ImportStats(0, 0, 0, 0, 0);
  }

  public ImportStats plus(ImportStats other) {
    return new ImportStats(
        read + other.read,
        created + other.created,
        updated + other.updated,
        skipped + other.skipped,
        failed + other.failed);
  }

  public ImportStats withUpdated(int extraUpdated) {
    return new ImportStats(read, created, updated + extraUpdated, skipped, failed);
  }
}
