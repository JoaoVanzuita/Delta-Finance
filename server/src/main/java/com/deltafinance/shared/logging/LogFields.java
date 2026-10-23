package com.deltafinance.shared.logging;

/** Chaves de MDC usadas nos logs estruturados. */
public final class LogFields {

  public static final String REQUEST_ID = "requestId";
  public static final String USER_ID = "userId";
  public static final String EVENT = "event";
  public static final String OUTCOME = "outcome";
  public static final String ERROR_CODE = "errorCode";
  public static final String DURATION_MS = "durationMs";

  private LogFields() {}
}
