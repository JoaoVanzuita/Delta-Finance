package com.deltafinance.shared.sync;

/** Sinaliza que a tentativa de sincronização em andamento deve parar antes da próxima escrita. */
public class SyncCancelledException extends RuntimeException {

  private final SyncCancellationRegistry.Reason reason;

  public SyncCancelledException(SyncCancellationRegistry.Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public SyncCancellationRegistry.Reason reason() {
    return reason;
  }
}
