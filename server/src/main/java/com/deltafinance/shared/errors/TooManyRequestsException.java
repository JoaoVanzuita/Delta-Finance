package com.deltafinance.shared.errors;

import org.springframework.http.HttpStatus;

/** Limite de tentativas/requisições atingido (HTTP 429), com indicação de espera. */
public class TooManyRequestsException extends ApiException {

  private final long retryAfterSeconds;

  public TooManyRequestsException(String message, long retryAfterSeconds) {
    super(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, message);
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
