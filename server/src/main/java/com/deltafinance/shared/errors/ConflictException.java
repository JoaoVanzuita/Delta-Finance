package com.deltafinance.shared.errors;

import org.springframework.http.HttpStatus;

/** Violação de unicidade ou de estado (HTTP 409). */
public class ConflictException extends ApiException {

  public ConflictException(String code, String message) {
    super(HttpStatus.CONFLICT, code, message);
  }
}
