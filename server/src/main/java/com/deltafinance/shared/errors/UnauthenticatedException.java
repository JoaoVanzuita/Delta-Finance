package com.deltafinance.shared.errors;

import org.springframework.http.HttpStatus;

/** Requisição sem sessão válida (HTTP 401). */
public class UnauthenticatedException extends ApiException {

  public UnauthenticatedException() {
    super(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Autenticação necessária.");
  }

  public UnauthenticatedException(String message) {
    super(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, message);
  }
}
